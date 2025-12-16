package com.sunny.job.worker.pekko.ddata;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.ddata.Key;
import org.apache.pekko.cluster.ddata.LWWMap;
import org.apache.pekko.cluster.ddata.LWWMapKey;
import org.apache.pekko.cluster.ddata.SelfUniqueAddress;
import org.apache.pekko.cluster.ddata.typed.javadsl.DistributedData;
import org.apache.pekko.cluster.ddata.typed.javadsl.Replicator;
import org.apache.pekko.cluster.ddata.typed.javadsl.ReplicatorMessageAdapter;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * 最大 JobRunId 管理器
 * <p>
 * 使用 Pekko Distributed Data (CRDT) 在集群中同步全局最大 jobRunId
 * 用于事件驱动的增量任务加载
 * <p>
 * 数据结构: LWWMap[String, Long]
 * - Key: Worker 地址
 * - Value: 该 Worker 发现的最大 jobRunId
 * <p>
 * 读取策略：取所有 Worker 上报值的最大值
 * 这样即使有并发更新，最终也能收敛到正确的全局最大值
 * <p>
 * 事件驱动机制：
 * - Worker 加载新任务后，更新 CRDT 中的 maxJobRunId
 * - CRDT 同步到其他 Worker
 * - 其他 Worker 检测到全局 maxJobRunId 增大，触发增量加载
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class MaxJobRunIdState {

    private static final Logger log = LoggerFactory.getLogger(MaxJobRunIdState.class);

    private static final String MAX_JOB_RUN_ID_KEY = "max-job-run-id";

    private final ActorSystem<?> system;
    private final SelfUniqueAddress selfUniqueAddress;
    private final String selfAddress;
    private final Key<LWWMap<String, Long>> dataKey;

    /**
     * 本地缓存：workerAddress → maxJobRunId
     */
    private final Map<String, Long> localCache = new ConcurrentHashMap<>();

    /**
     * 当前已知的全局最大 jobRunId
     */
    private volatile long currentMaxId = 0;

    /**
     * maxJobRunId 变化监听器
     */
    private volatile LongConsumer maxIdChangeListener;

    /**
     * 内部 Actor 引用
     */
    private ActorRef<InternalCommand> internalActor;

    public MaxJobRunIdState(ActorSystem<?> system) {
        this.system = system;
        this.selfUniqueAddress = DistributedData.get(system).selfUniqueAddress();
        this.selfAddress = Cluster.get(system).selfMember().address().toString();
        this.dataKey = LWWMapKey.create(MAX_JOB_RUN_ID_KEY);

        // 创建内部 Actor 处理 CRDT 交互
        this.internalActor = system.systemActorOf(
                InternalActor.create(this),
                "max-job-run-id-manager",
                Props.empty()
        );

        log.info("MaxJobRunIdState 初始化完成，selfAddress={}", selfAddress);
    }

    /**
     * 设置 maxJobRunId 变化监听器
     *
     * @param listener 监听器，参数为新的全局 maxJobRunId
     */
    public void subscribe(LongConsumer listener) {
        this.maxIdChangeListener = listener;
        log.info("已设置 maxJobRunId 变化监听器");
    }

    /**
     * 更新本 Worker 发现的最大 jobRunId
     * <p>
     * 只有当新值大于本 Worker 之前上报的值时才更新
     *
     * @param maxJobRunId 本 Worker 发现的最大 jobRunId
     */
    public void updateMaxId(long maxJobRunId) {
        Long currentValue = localCache.get(selfAddress);
        if (currentValue != null && maxJobRunId <= currentValue) {
            return;
        }

        log.debug("更新本 Worker 的 maxJobRunId: {} -> {}", currentValue, maxJobRunId);

        // 更新本地缓存
        localCache.put(selfAddress, maxJobRunId);

        // 发送更新命令到内部 Actor
        internalActor.tell(new UpdateCommand(maxJobRunId));

        // 检查是否影响全局最大值
        if (maxJobRunId > currentMaxId) {
            currentMaxId = maxJobRunId;
        }
    }

    /**
     * 获取当前全局最大 jobRunId
     *
     * @return 全局最大 jobRunId
     */
    public long getGlobalMaxId() {
        return currentMaxId;
    }

    /**
     * 处理 CRDT 数据变化（由内部 Actor 调用）
     */
    @SuppressWarnings("unchecked")
    void onDataChanged(LWWMap<String, Long> data) {
        // 更新本地缓存
        // getEntries() 返回的实际上是 scala.collection.immutable.Map，但泛型擦除后编译器看到的是 java.util.Map
        // 使用强制转换获取 Scala Map
        Object entriesObj = data.getEntries();
        if (entriesObj instanceof scala.collection.Map) {
            scala.collection.Map<String, Long> scalaMap = (scala.collection.Map<String, Long>) entriesObj;
            scala.collection.Iterator<scala.Tuple2<String, Long>> iterator = scalaMap.iterator();
            while (iterator.hasNext()) {
                scala.Tuple2<String, Long> entry = iterator.next();
                localCache.put(entry._1(), entry._2());
            }
        }

        // 计算新的全局最大值
        long newMaxId = calculateGlobalMaxId();

        // 检查是否有变化
        if (newMaxId > currentMaxId) {
            long oldMaxId = currentMaxId;
            currentMaxId = newMaxId;

            log.info("检测到全局 maxJobRunId 变化: {} -> {}", oldMaxId, newMaxId);

            // 通知监听器
            if (maxIdChangeListener != null) {
                maxIdChangeListener.accept(newMaxId);
            }
        }
    }

    /**
     * 计算全局最大 jobRunId
     *
     * @return 所有 Worker 上报值的最大值
     */
    private long calculateGlobalMaxId() {
        long maxId = 0;
        for (Long value : localCache.values()) {
            if (value > maxId) {
                maxId = value;
            }
        }
        return maxId;
    }

    /**
     * 获取本地缓存大小（调试用）
     */
    public int getCacheSize() {
        return localCache.size();
    }

    // ============ 内部命令接口 ============

    interface InternalCommand {}

    static class UpdateCommand implements InternalCommand {
        final long maxJobRunId;

        UpdateCommand(long maxJobRunId) {
            this.maxJobRunId = maxJobRunId;
        }
    }

    private static class InternalSubscribeResponse implements InternalCommand {
        final Replicator.SubscribeResponse<LWWMap<String, Long>> response;

        InternalSubscribeResponse(Replicator.SubscribeResponse<LWWMap<String, Long>> response) {
            this.response = response;
        }
    }

    private static class InternalUpdateResponse implements InternalCommand {
        final Replicator.UpdateResponse<LWWMap<String, Long>> response;

        InternalUpdateResponse(Replicator.UpdateResponse<LWWMap<String, Long>> response) {
            this.response = response;
        }
    }

    // ============ 内部 Actor ============

    private static class InternalActor extends AbstractBehavior<InternalCommand> {

        private final MaxJobRunIdState manager;
        private final ReplicatorMessageAdapter<InternalCommand, LWWMap<String, Long>> replicatorAdapter;
        private final SelfUniqueAddress selfUniqueAddress;

        static Behavior<InternalCommand> create(MaxJobRunIdState manager) {
            return Behaviors.setup(context -> new InternalActor(context, manager));
        }

        private InternalActor(ActorContext<InternalCommand> context, MaxJobRunIdState manager) {
            super(context);
            this.manager = manager;
            this.selfUniqueAddress = DistributedData.get(context.getSystem()).selfUniqueAddress();

            // 创建 Replicator 消息适配器
            ActorRef<Replicator.Command> replicator = DistributedData.get(context.getSystem()).replicator();
            this.replicatorAdapter = new ReplicatorMessageAdapter<>(context, replicator, java.time.Duration.ofSeconds(5));

            // 订阅 CRDT 数据变化
            replicatorAdapter.subscribe(manager.dataKey, InternalSubscribeResponse::new);

            log.info("内部 Actor 初始化完成，已订阅 CRDT 数据变化");
        }

        @Override
        public Receive<InternalCommand> createReceive() {
            return newReceiveBuilder()
                    .onMessage(UpdateCommand.class, this::onUpdate)
                    .onMessage(InternalSubscribeResponse.class, this::onSubscribeResponse)
                    .onMessage(InternalUpdateResponse.class, this::onUpdateResponse)
                    .build();
        }

        private Behavior<InternalCommand> onUpdate(UpdateCommand cmd) {
            replicatorAdapter.askUpdate(
                    askReplyTo -> new Replicator.Update<>(
                            manager.dataKey,
                            LWWMap.empty(),
                            Replicator.writeLocal(),
                            askReplyTo,
                            existing -> existing.put(selfUniqueAddress, manager.selfAddress, cmd.maxJobRunId)
                    ),
                    InternalUpdateResponse::new
            );
            return this;
        }

        private Behavior<InternalCommand> onSubscribeResponse(InternalSubscribeResponse wrapper) {
            Replicator.SubscribeResponse<LWWMap<String, Long>> response = wrapper.response;
            if (response instanceof Replicator.Changed) {
                @SuppressWarnings("unchecked")
                Replicator.Changed<LWWMap<String, Long>> changed = (Replicator.Changed<LWWMap<String, Long>>) response;
                manager.onDataChanged(changed.get(manager.dataKey));
            }
            return this;
        }

        private Behavior<InternalCommand> onUpdateResponse(InternalUpdateResponse wrapper) {
            Replicator.UpdateResponse<LWWMap<String, Long>> response = wrapper.response;
            if (response instanceof Replicator.UpdateSuccess) {
                log.debug("CRDT 更新成功");
            } else if (response instanceof Replicator.UpdateFailure) {
                log.warn("CRDT 更新失败: {}", response);
            }
            return this;
        }
    }
}
