package com.sunny.kg.client;

import lombok.Data;

import java.time.Duration;

/**
 * 知识库客户端配置
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
public class KnowledgeClientConfig {

    /**
     * Neo4j 连接 URI
     */
    private String neo4jUri;

    /**
     * Neo4j 用户名
     */
    private String neo4jUsername;

    /**
     * Neo4j 密码
     */
    private String neo4jPassword;

    /**
     * Neo4j 数据库名称
     */
    private String neo4jDatabase = "neo4j";

    /**
     * 数据来源标识
     */
    private String producer;

    /**
     * 是否启用异步模式
     */
    private boolean async = true;

    /**
     * 批量写入大小
     */
    private int batchSize = 100;

    /**
     * 刷新间隔
     */
    private Duration flushInterval = Duration.ofSeconds(5);

    /**
     * 连接池最大连接数
     */
    private int maxConnectionPoolSize = 50;

    /**
     * 连接获取超时
     */
    private Duration connectionAcquisitionTimeout = Duration.ofSeconds(60);

    /**
     * 优雅关闭超时
     */
    private Duration shutdownTimeout = Duration.ofSeconds(30);

    /**
     * 是否启用死信队列
     */
    private boolean dlqEnabled = true;

    /**
     * 死信队列目录（为空则使用内存队列）
     */
    private String dlqDirectory;

    /**
     * 内存死信队列最大容量
     */
    private int dlqMaxSize = 10000;

    // ==================== P1 特性 ====================

    /**
     * 是否启用熔断器
     */
    private boolean circuitBreakerEnabled = true;

    /**
     * 熔断器失败率阈值（0-100）
     */
    private int circuitBreakerFailureRateThreshold = 50;

    /**
     * 熔断器滑动窗口大小
     */
    private int circuitBreakerSlidingWindowSize = 10;

    /**
     * 熔断后等待时间（秒）
     */
    private int circuitBreakerWaitDurationSeconds = 30;

    /**
     * 是否启用链路追踪
     */
    private boolean tracingEnabled = false;

    /**
     * 是否启用数据校验
     */
    private boolean validationEnabled = true;

    // ==================== P2 特性 ====================

    /**
     * 是否启用限流
     */
    private boolean rateLimitEnabled = false;

    /**
     * 每秒允许的请求数
     */
    private int rateLimitPerSecond = 1000;

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 是否启用幂等性检查
     */
    private boolean idempotentEnabled = false;

    /**
     * 幂等性 TTL（秒）
     */
    private int idempotentTtlSeconds = 3600;

}
