package com.sunny.kg.metrics;

/**
 * 知识库客户端指标接口
 * <p>
 * SDK 只提供数据接口，使用方自行决定如何暴露（Prometheus/Micrometer/日志等）
 *
 * @author Sunny
 * @since 2025-12-11
 */
public interface KnowledgeMetrics {

    /**
     * 获取写入总数
     */
    long getEmitTotal();

    /**
     * 获取写入成功数
     */
    long getEmitSuccess();

    /**
     * 获取写入失败数
     */
    long getEmitFailed();

    /**
     * 获取平均延迟（毫秒）
     */
    double getAvgLatencyMs();

    /**
     * 获取最大延迟（毫秒）
     */
    long getMaxLatencyMs();

    /**
     * 获取当前队列大小
     */
    int getQueueSize();

    /**
     * 获取死信队列大小
     */
    int getDlqSize();

    /**
     * 获取活跃连接数
     */
    int getActiveConnections();

    /**
     * 重置指标
     */
    void reset();

    /**
     * 获取快照（用于日志输出）
     */
    MetricsSnapshot snapshot();

}
