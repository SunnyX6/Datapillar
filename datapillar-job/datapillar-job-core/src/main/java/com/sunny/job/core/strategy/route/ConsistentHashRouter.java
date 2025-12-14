package com.sunny.job.core.strategy.route;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 一致性哈希路由器
 * <p>
 * 使用虚拟节点保证分布均匀，相同 key 总是路由到同一个 Worker
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class ConsistentHashRouter {

    /**
     * 默认虚拟节点数量
     */
    private static final int DEFAULT_VIRTUAL_NODES = 160;

    /**
     * 哈希环
     */
    private final TreeMap<Long, WorkerInfo> hashRing;

    /**
     * 虚拟节点数量
     */
    private final int virtualNodes;

    private ConsistentHashRouter(int virtualNodes) {
        this.hashRing = new TreeMap<>();
        this.virtualNodes = virtualNodes;
    }

    /**
     * 创建一致性哈希路由器
     *
     * @param workers Worker 列表
     * @return 路由器实例
     */
    public static ConsistentHashRouter create(List<WorkerInfo> workers) {
        return create(workers, DEFAULT_VIRTUAL_NODES);
    }

    /**
     * 创建一致性哈希路由器
     *
     * @param workers      Worker 列表
     * @param virtualNodes 每个物理节点的虚拟节点数量
     * @return 路由器实例
     */
    public static ConsistentHashRouter create(List<WorkerInfo> workers, int virtualNodes) {
        ConsistentHashRouter router = new ConsistentHashRouter(virtualNodes);
        for (WorkerInfo worker : workers) {
            router.addWorker(worker);
        }
        return router;
    }

    /**
     * 添加 Worker 节点
     *
     * @param worker Worker 信息
     */
    public void addWorker(WorkerInfo worker) {
        for (int i = 0; i < virtualNodes; i++) {
            String virtualKey = worker.address() + "#" + i;
            long hash = hash(virtualKey);
            hashRing.put(hash, worker);
        }
    }

    /**
     * 移除 Worker 节点
     *
     * @param worker Worker 信息
     */
    public void removeWorker(WorkerInfo worker) {
        for (int i = 0; i < virtualNodes; i++) {
            String virtualKey = worker.address() + "#" + i;
            long hash = hash(virtualKey);
            hashRing.remove(hash);
        }
    }

    /**
     * 根据 key 选择 Worker
     *
     * @param key 路由键
     * @return 选中的 Worker，若无可用节点返回 null
     */
    public WorkerInfo select(String key) {
        if (hashRing.isEmpty()) {
            return null;
        }

        long hash = hash(key);

        // 顺时针查找第一个节点
        SortedMap<Long, WorkerInfo> tailMap = hashRing.tailMap(hash);
        Long targetHash = tailMap.isEmpty() ? hashRing.firstKey() : tailMap.firstKey();

        return hashRing.get(targetHash);
    }

    /**
     * 获取节点数量（物理节点）
     */
    public int size() {
        return hashRing.size() / virtualNodes;
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return hashRing.isEmpty();
    }

    /**
     * 计算哈希值
     * <p>
     * 使用 MD5 并取前 8 字节作为 long 值
     */
    private static long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));

            // 取前 8 字节转为 long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // MD5 算法一定存在，不会抛出此异常
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }
}
