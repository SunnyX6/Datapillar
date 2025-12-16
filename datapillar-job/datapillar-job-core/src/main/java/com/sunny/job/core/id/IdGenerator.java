package com.sunny.job.core.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式 ID 生成器（Snowflake 变种）
 * <p>
 * 64 位结构：
 * - 1 位：符号位（始终为 0）
 * - 41 位：时间戳（毫秒级，可用 69 年）
 * - 10 位：节点 ID（0-1023，从 Pekko Cluster 地址哈希获取）
 * - 12 位：序列号（每毫秒 4096 个）
 * <p>
 * 设计特点：
 * - 节点 ID 自动从 Pekko Cluster 地址获取，无需配置
 * - Server 和 Worker 在同一集群，自动获得不同节点 ID
 * - 线程安全，支持高并发
 * - 时钟回拨保护
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public class IdGenerator {

    /**
     * 起始时间戳（2024-01-01 00:00:00 UTC）
     */
    private static final long EPOCH = 1704067200000L;

    /**
     * 节点 ID 位数
     */
    private static final int NODE_ID_BITS = 10;

    /**
     * 序列号位数
     */
    private static final int SEQUENCE_BITS = 12;

    /**
     * 节点 ID 最大值（1023）
     */
    private static final int MAX_NODE_ID = (1 << NODE_ID_BITS) - 1;

    /**
     * 序列号最大值（4095）
     */
    private static final int MAX_SEQUENCE = (1 << SEQUENCE_BITS) - 1;

    /**
     * 节点 ID 左移位数
     */
    private static final int NODE_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 时间戳左移位数
     */
    private static final int TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS;

    /**
     * 节点 ID
     */
    private final int nodeId;

    /**
     * 上一次生成 ID 的时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 当前毫秒内的序列号
     */
    private int sequence = 0;

    /**
     * 已生成的 ID 计数（用于监控）
     */
    private final AtomicLong generatedCount = new AtomicLong(0);

    /**
     * 构造函数
     *
     * @param nodeId 节点 ID（0-1023）
     */
    public IdGenerator(int nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("节点 ID 必须在 0-" + MAX_NODE_ID + " 之间，当前值: " + nodeId);
        }
        this.nodeId = nodeId;
    }

    /**
     * 从地址字符串创建 IdGenerator
     * <p>
     * 用于从 Pekko Cluster 地址自动获取节点 ID
     *
     * @param address 地址字符串（如 "pekko://datapillar-job@127.0.0.1:2551"）
     * @return IdGenerator 实例
     */
    public static IdGenerator fromAddress(String address) {
        int nodeId = Math.abs(address.hashCode()) % (MAX_NODE_ID + 1);
        return new IdGenerator(nodeId);
    }

    /**
     * 生成下一个 ID
     *
     * @return 全局唯一 ID
     */
    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        // 时钟回拨检测
        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                // 回拨 5ms 以内，等待追上
                try {
                    Thread.sleep(offset + 1);
                    currentTimestamp = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("时钟回拨等待被中断", e);
                }
            } else {
                throw new RuntimeException("时钟回拨超过 5ms，拒绝生成 ID，回拨: " + offset + "ms");
            }
        }

        if (currentTimestamp == lastTimestamp) {
            // 同一毫秒内，序列号递增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号溢出，等待下一毫秒
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 新的毫秒，序列号重置
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;
        generatedCount.incrementAndGet();

        // 组装 ID
        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                | ((long) nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    /**
     * 批量生成 ID
     *
     * @param count 数量
     * @return ID 数组
     */
    public long[] nextIds(int count) {
        if (count <= 0) {
            return new long[0];
        }
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = nextId();
        }
        return ids;
    }

    /**
     * 等待下一毫秒
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 获取节点 ID
     */
    public int getNodeId() {
        return nodeId;
    }

    /**
     * 获取已生成的 ID 数量（用于监控）
     */
    public long getGeneratedCount() {
        return generatedCount.get();
    }

    /**
     * 基于 eventId 和 entityId 生成确定性 ID
     * <p>
     * 用于 Worker 自行计算 workflowRunId 和 jobRunId：
     * - workflowRunId = deterministicId(eventId, workflowId)
     * - jobRunId = deterministicId(eventId, jobId)
     * <p>
     * 算法：使用 MurmurHash3 风格的混合函数，确保分布均匀且确定性
     *
     * @param eventId  广播事件唯一标识
     * @param entityId 实体 ID（workflowId 或 jobId）
     * @return 确定性生成的唯一 ID（正数）
     */
    public static long deterministicId(String eventId, long entityId) {
        // 将 eventId 转为 long（使用 hashCode 的高低位混合）
        long eventHash = eventId.hashCode();
        eventHash = eventHash ^ (eventHash >>> 16);
        eventHash = eventHash * 0x85ebca6bL;
        eventHash = eventHash ^ (eventHash >>> 13);
        eventHash = eventHash * 0xc2b2ae35L;
        eventHash = eventHash ^ (eventHash >>> 16);

        // 混合 eventHash 和 entityId
        long mixed = eventHash ^ entityId;
        mixed = mixed * 0x9e3779b97f4a7c15L;  // 黄金比例常数
        mixed = mixed ^ (mixed >>> 30);
        mixed = mixed * 0xbf58476d1ce4e5b9L;
        mixed = mixed ^ (mixed >>> 27);
        mixed = mixed * 0x94d049bb133111ebL;
        mixed = mixed ^ (mixed >>> 31);

        // 确保为正数（清除符号位）
        return mixed & Long.MAX_VALUE;
    }

    /**
     * 解析 ID 中的时间戳
     *
     * @param id ID
     * @return 时间戳（毫秒）
     */
    public static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    /**
     * 解析 ID 中的节点 ID
     *
     * @param id ID
     * @return 节点 ID
     */
    public static int parseNodeId(long id) {
        return (int) ((id >> NODE_ID_SHIFT) & MAX_NODE_ID);
    }

    /**
     * 解析 ID 中的序列号
     *
     * @param id ID
     * @return 序列号
     */
    public static int parseSequence(long id) {
        return (int) (id & MAX_SEQUENCE);
    }

    @Override
    public String toString() {
        return "IdGenerator{nodeId=" + nodeId + ", generatedCount=" + generatedCount.get() + "}";
    }
}
