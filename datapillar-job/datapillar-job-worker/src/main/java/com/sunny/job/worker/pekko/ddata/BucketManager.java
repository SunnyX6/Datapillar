package com.sunny.job.worker.pekko.ddata;

import com.sunny.job.core.strategy.route.ConsistentHashRouter;
import com.sunny.job.core.strategy.route.WorkerInfo;
import com.sunny.job.worker.domain.mapper.JobBucketLeaseMapper;
import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Bucket 管理器
 * <p>
 * 使用本地缓存 + DB 持久化管理 Bucket 所有权
 * 不使用 CRDT（避免序列化问题，减少网络开销）
 * <p>
 * 数据结构: Map[Integer, BucketLease]
 * - Key: Bucket ID (0 ~ BUCKET_COUNT-1)
 * - Value: BucketLease (包含 workerAddress, leaseTime)
 * <p>
 * Bucket 分配机制（一致性哈希）：
 * - 使用 ConsistentHashRouter 计算每个 Bucket 应该归属的 Worker
 * - Worker 加入/离开时，只迁移 bucketCount/N 个 Bucket
 * - 虚拟节点（160 个）保证分布均匀
 * <p>
 * Bucket 转移机制：
 * - 主动下线：Worker shutdown 时主动释放 Bucket
 * - 故障下线：监听 MemberRemoved 事件，清理故障节点的 Bucket
 * - 兜底超时：30 秒未续租，Bucket 可被抢占
 * <p>
 * 持久化机制：
 * - 认领成功后异步写入 DB
 * - 释放时同步删除 DB 记录
 * - Worker 启动时从 DB 恢复之前持有的 Bucket 列表
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public class BucketManager {

    private static final Logger log = LoggerFactory.getLogger(BucketManager.class);

    private final ActorSystem<?> system;
    private final Cluster cluster;
    private final String selfAddress;
    private final int bucketCount;

    /**
     * Worker 状态管理器（用于获取存活 Worker 列表）
     */
    private final WorkerManager workerManager;

    /**
     * DB 持久化 Mapper（可选，为 null 时不持久化）
     */
    private final JobBucketLeaseMapper bucketLeaseMapper;

    /**
     * 异步执行器（用于 DB 持久化）
     */
    private final ExecutorService asyncExecutor;

    /**
     * 本地缓存：bucketId → BucketLease
     */
    private final Map<Integer, BucketLease> localCache = new ConcurrentHashMap<>();

    /**
     * 当前 Worker 持有的 Bucket
     */
    private final Set<Integer> myBuckets = ConcurrentHashMap.newKeySet();

    /**
     * 当前一致性哈希计算出的应持有 Bucket（可能尚未全部认领成功）
     */
    private final Set<Integer> expectedBuckets = ConcurrentHashMap.newKeySet();

    /**
     * Bucket 获得监听器
     */
    private volatile Consumer<Integer> bucketAcquiredListener;

    /**
     * Bucket 丢失监听器
     */
    private volatile Consumer<Integer> bucketLostListener;

    /**
     * 是否已订阅
     */
    private volatile boolean subscribed = false;

    public BucketManager(ActorSystem<?> system, WorkerManager workerManager) {
        this(system, BucketLease.DEFAULT_BUCKET_COUNT, workerManager, null);
    }

    public BucketManager(ActorSystem<?> system, int bucketCount, WorkerManager workerManager) {
        this(system, bucketCount, workerManager, null);
    }

    public BucketManager(ActorSystem<?> system, int bucketCount,
                              WorkerManager workerManager,
                              JobBucketLeaseMapper bucketLeaseMapper) {
        this.system = system;
        this.bucketCount = bucketCount;
        this.workerManager = workerManager;
        this.bucketLeaseMapper = bucketLeaseMapper;
        this.cluster = Cluster.get(system);
        this.selfAddress = cluster.selfMember().address().toString();
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
        log.info("BucketManager 初始化完成，selfAddress={}, bucketCount={}, dbPersistence={}",
                selfAddress, bucketCount, bucketLeaseMapper != null);
    }

    /**
     * 订阅 Bucket 状态变化和集群事件
     *
     * @param bucketAcquiredListener Bucket 获得监听器
     * @param bucketLostListener     Bucket 丢失监听器
     */
    public void subscribe(Consumer<Integer> bucketAcquiredListener, Consumer<Integer> bucketLostListener) {
        if (subscribed) {
            log.warn("已订阅，忽略重复订阅");
            return;
        }

        this.bucketAcquiredListener = bucketAcquiredListener;
        this.bucketLostListener = bucketLostListener;

        // 订阅集群成员事件
        subscribeToClusterEvents();

        subscribed = true;
        log.info("已订阅 Bucket 状态变化和集群事件");
    }

    /**
     * 订阅集群成员事件
     * <p>
     * 监听 MemberUp 和 MemberRemoved 事件：
     * - MemberUp: 新节点加入或自己变成 Up，触发 Bucket 再平衡
     * - MemberRemoved: 节点移除，清理该节点的 Bucket 并再平衡
     */
    private void subscribeToClusterEvents() {
        ActorRef<ClusterEvent.MemberEvent> memberEventAdapter =
                system.systemActorOf(
                        org.apache.pekko.actor.typed.javadsl.Behaviors.receive(ClusterEvent.MemberEvent.class)
                                .onMessage(ClusterEvent.MemberUp.class, this::onMemberUp)
                                .onMessage(ClusterEvent.MemberRemoved.class, this::onMemberRemoved)
                                .build(),
                        "bucket-cluster-event-listener",
                        org.apache.pekko.actor.typed.Props.empty()
                );

        cluster.subscriptions().tell(new Subscribe<>(memberEventAdapter, ClusterEvent.MemberEvent.class));
    }

    /**
     * 处理成员加入事件
     * <p>
     * 当有新节点加入（包括自己变成 Up）时，触发 Bucket 再平衡
     */
    private org.apache.pekko.actor.typed.Behavior<ClusterEvent.MemberEvent> onMemberUp(
            ClusterEvent.MemberUp event) {
        Member upMember = event.member();
        String upAddressStr = upMember.address().toString();

        log.info("检测到节点加入: address={}, isSelf={}", upAddressStr, upAddressStr.equals(selfAddress));

        // 触发 Bucket 再平衡
        rebalanceBuckets();

        return org.apache.pekko.actor.typed.javadsl.Behaviors.same();
    }

    /**
     * 处理成员移除事件
     * <p>
     * 当节点 Down 时，清理该节点持有的所有 Bucket 并触发再平衡
     */
    private org.apache.pekko.actor.typed.Behavior<ClusterEvent.MemberEvent> onMemberRemoved(
            ClusterEvent.MemberRemoved event) {
        Member removedMember = event.member();
        Address removedAddress = removedMember.address();
        String removedAddressStr = removedAddress.toString();

        log.info("检测到节点移除: address={}", removedAddressStr);

        // 清理该节点持有的所有 Bucket
        List<Integer> bucketsToRelease = new ArrayList<>();
        for (Map.Entry<Integer, BucketLease> entry : localCache.entrySet()) {
            if (entry.getValue().isOwnedBy(removedAddressStr)) {
                bucketsToRelease.add(entry.getKey());
            }
        }

        if (!bucketsToRelease.isEmpty()) {
            log.info("清理故障节点的 Bucket: address={}, buckets={}", removedAddressStr, bucketsToRelease);
            for (Integer bucketId : bucketsToRelease) {
                localCache.remove(bucketId);
            }
        }

        // 触发 Bucket 再平衡，接管故障节点的 Bucket
        rebalanceBuckets();

        return org.apache.pekko.actor.typed.javadsl.Behaviors.same();
    }

    /**
     * 认领空闲 Bucket（基于一致性哈希）
     * <p>
     * 使用一致性哈希计算当前 Worker 应该持有的 Bucket，
     * 只认领属于自己的 Bucket，保证负载均衡和最小迁移。
     *
     * @param maxBuckets 最大认领数量（-1 表示不限制）
     * @return 成功认领的 Bucket ID 列表
     */
    public Set<Integer> acquireBuckets(int maxBuckets) {
        // 获取所有存活的 Worker
        List<WorkerInfo> aliveWorkers = workerManager.getAliveWorkers();
        if (aliveWorkers.isEmpty()) {
            // 没有其他 Worker，自己认领所有 Bucket
            aliveWorkers = List.of(WorkerInfo.of(selfAddress));
        }

        // 基于一致性哈希计算应该持有的 Bucket
        Set<Integer> shouldOwn = calculateMyBuckets(aliveWorkers);
        expectedBuckets.clear();
        expectedBuckets.addAll(shouldOwn);

        log.info("一致性哈希计算 Bucket 分配：workerCount={}, shouldOwn={}",
                aliveWorkers.size(), shouldOwn.size());

        // 只认领属于自己的 Bucket
        Set<Integer> acquired = new HashSet<>();
        int count = 0;

        for (Integer bucketId : shouldOwn) {
            if (maxBuckets > 0 && count >= maxBuckets) {
                break;
            }

            if (tryAcquireBucket(bucketId)) {
                acquired.add(bucketId);
                count++;
            }
        }

        // 释放不再属于自己的 Bucket
        releaseNonOwnedBuckets(shouldOwn);

        if (!acquired.isEmpty()) {
            log.info("成功认领 {} 个 Bucket: {}", acquired.size(), acquired);
        }

        return acquired;
    }

    /**
     * 基于一致性哈希计算当前 Worker 应该持有的 Bucket
     *
     * @param aliveWorkers 存活的 Worker 列表
     * @return 应该持有的 Bucket ID 集合
     */
    private Set<Integer> calculateMyBuckets(List<WorkerInfo> aliveWorkers) {
        ConsistentHashRouter router = ConsistentHashRouter.create(aliveWorkers);
        Set<Integer> myBucketIds = new HashSet<>();

        for (int bucketId = 0; bucketId < bucketCount; bucketId++) {
            WorkerInfo owner = router.select(String.valueOf(bucketId));
            if (owner != null && owner.address().equals(selfAddress)) {
                myBucketIds.add(bucketId);
            }
        }

        return myBucketIds;
    }

    /**
     * 释放不再属于当前 Worker 的 Bucket
     *
     * @param shouldOwn 应该持有的 Bucket 集合
     */
    private void releaseNonOwnedBuckets(Set<Integer> shouldOwn) {
        Set<Integer> toRelease = new HashSet<>();
        for (Integer bucketId : myBuckets) {
            if (!shouldOwn.contains(bucketId)) {
                toRelease.add(bucketId);
            }
        }

        if (!toRelease.isEmpty()) {
            log.info("释放不再属于当前 Worker 的 Bucket: {}", toRelease);
            for (Integer bucketId : toRelease) {
                releaseBucket(bucketId);
            }
        }
    }

    /**
     * 当 Worker 列表变化时重新平衡 Bucket
     * <p>
     * 应在 WorkerManager 状态变化时调用
     */
    public void rebalanceBuckets() {
        log.info("触发 Bucket 再平衡...");
        acquireBuckets(-1);
    }

    /**
     * 认领所有空闲 Bucket
     *
     * @return 成功认领的 Bucket ID 列表
     */
    public Set<Integer> acquireAllAvailableBuckets() {
        return acquireBuckets(-1);
    }

    /**
     * 认领属于指定 shard 的空闲 Bucket
     * <p>
     * 用于分片调度模式，每个 Scheduler 只认领 bucketId % shardCount == shardId 的 Bucket
     *
     * @param shardId    分片 ID
     * @param shardCount 总分片数
     * @return 成功认领的 Bucket ID 列表
     */
    public Set<Integer> acquireAvailableBucketsForShard(int shardId, int shardCount) {
        // 获取所有存活的 Worker
        List<WorkerInfo> aliveWorkers = workerManager.getAliveWorkers();
        if (aliveWorkers.isEmpty()) {
            aliveWorkers = List.of(WorkerInfo.of(selfAddress));
        }

        // 基于一致性哈希计算应该持有的 Bucket
        Set<Integer> shouldOwn = calculateMyBuckets(aliveWorkers);

        // 只认领属于指定 shard 的 Bucket
        Set<Integer> acquired = new HashSet<>();
        for (Integer bucketId : shouldOwn) {
            // 检查是否属于当前 shard
            if (bucketId % shardCount != shardId) {
                continue;
            }

            if (tryAcquireBucket(bucketId)) {
                acquired.add(bucketId);
            }
        }

        log.info("分片认领 Bucket: shardId={}, shardCount={}, acquired={}",
                shardId, shardCount, acquired.size());
        return acquired;
    }

    /**
     * 尝试认领单个 Bucket
     *
     * @param bucketId Bucket ID
     * @return true 如果认领成功
     */
    public boolean tryAcquireBucket(int bucketId) {
        BucketLease currentLease = localCache.get(bucketId);

        // 检查是否可以认领
        if (currentLease != null && !currentLease.isExpired() && !currentLease.isOwnedBy(selfAddress)) {
            // Bucket 已被其他 Worker 持有且未过期
            return false;
        }

        // 尝试认领
        BucketLease newLease = BucketLease.create(bucketId, selfAddress);
        localCache.put(bucketId, newLease);
        myBuckets.add(bucketId);

        // 异步持久化到 DB
        persistLeaseAsync(bucketId);

        log.debug("认领 Bucket: bucketId={}", bucketId);
        return true;
    }

    /**
     * 续租所有持有的 Bucket
     */
    public void renewAllBuckets() {
        for (Integer bucketId : myBuckets) {
            renewBucket(bucketId);
        }
        log.debug("续租 {} 个 Bucket", myBuckets.size());
    }

    /**
     * 续租单个 Bucket
     *
     * @param bucketId Bucket ID
     */
    public void renewBucket(int bucketId) {
        BucketLease lease = localCache.get(bucketId);
        if (lease != null && lease.isOwnedBy(selfAddress)) {
            BucketLease renewedLease = lease.renew();
            localCache.put(bucketId, renewedLease);
        }
    }

    /**
     * 释放单个 Bucket
     *
     * @param bucketId Bucket ID
     */
    public void releaseBucket(int bucketId) {
        log.info("释放 Bucket: bucketId={}", bucketId);

        myBuckets.remove(bucketId);
        localCache.remove(bucketId);

        // 从 DB 删除
        deleteLeaseFromDb(bucketId);
    }

    /**
     * 释放所有持有的 Bucket
     * <p>
     * Worker shutdown 时调用
     */
    public void releaseAllBuckets() {
        log.info("释放所有 Bucket: count={}", myBuckets.size());
        for (Integer bucketId : new HashSet<>(myBuckets)) {
            releaseBucket(bucketId);
        }
    }

    /**
     * 获取当前 Worker 持有的 Bucket 列表
     *
     * @return Bucket ID 集合
     */
    public Set<Integer> getMyBuckets() {
        return new HashSet<>(myBuckets);
    }

    /**
     * 检查是否持有指定 Bucket
     *
     * @param bucketId Bucket ID
     * @return true 如果持有
     */
    public boolean hasBucket(int bucketId) {
        return myBuckets.contains(bucketId);
    }

    /**
     * 获取 Bucket 总数
     *
     * @return Bucket 数量
     */
    public int getBucketCount() {
        return bucketCount;
    }

    /**
     * 获取自身地址
     *
     * @return Worker 地址
     */
    public String getSelfAddress() {
        return selfAddress;
    }

    // ==================== DB 持久化方法 ====================

    /**
     * 从 DB 恢复之前持有的 Bucket 列表
     * <p>
     * Worker 启动时调用，用于优先认领之前持有的 Bucket
     *
     * @return 之前持有的 Bucket ID 列表，如果没有 Mapper 则返回空列表
     */
    public List<Integer> recoverFromDb() {
        if (bucketLeaseMapper == null) {
            return List.of();
        }
        try {
            List<Integer> buckets = bucketLeaseMapper.selectBucketsByWorker(selfAddress);
            log.info("从 DB 恢复 Bucket 列表: count={}, buckets={}", buckets.size(), buckets);
            return buckets;
        } catch (Exception e) {
            log.error("从 DB 恢复 Bucket 列表失败", e);
            return List.of();
        }
    }

    /**
     * 异步持久化租约到 DB
     *
     * @param bucketId Bucket ID
     */
    private void persistLeaseAsync(int bucketId) {
        if (bucketLeaseMapper == null) {
            return;
        }
        asyncExecutor.execute(() -> {
            try {
                bucketLeaseMapper.upsert(bucketId, selfAddress, System.currentTimeMillis());
                log.debug("持久化 Bucket 租约到 DB: bucketId={}", bucketId);
            } catch (Exception e) {
                log.error("持久化 Bucket 租约失败: bucketId={}", bucketId, e);
            }
        });
    }

    /**
     * 从 DB 删除租约
     *
     * @param bucketId Bucket ID
     */
    private void deleteLeaseFromDb(int bucketId) {
        if (bucketLeaseMapper == null) {
            return;
        }
        try {
            bucketLeaseMapper.deleteByBucketId(bucketId);
            log.debug("从 DB 删除 Bucket 租约: bucketId={}", bucketId);
        } catch (Exception e) {
            log.error("从 DB 删除 Bucket 租约失败: bucketId={}", bucketId, e);
        }
    }

    /**
     * 从 DB 删除当前 Worker 的所有租约
     * <p>
     * Worker 下线时调用
     */
    private void deleteAllLeasesFromDb() {
        if (bucketLeaseMapper == null) {
            return;
        }
        try {
            int deleted = bucketLeaseMapper.deleteByWorker(selfAddress);
            log.info("从 DB 删除所有 Bucket 租约: count={}", deleted);
        } catch (Exception e) {
            log.error("从 DB 删除所有 Bucket 租约失败", e);
        }
    }
}
