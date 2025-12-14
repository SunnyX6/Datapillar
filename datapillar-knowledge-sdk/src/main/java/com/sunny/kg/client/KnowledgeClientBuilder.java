package com.sunny.kg.client;

import com.sunny.kg.internal.DefaultKnowledgeClient;

import java.time.Duration;

/**
 * 知识库客户端构建器
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class KnowledgeClientBuilder {

    private final KnowledgeClientConfig config = new KnowledgeClientConfig();

    /**
     * 配置 Neo4j 连接
     *
     * @param uri      连接 URI (如: bolt://localhost:7687)
     * @param username 用户名
     * @param password 密码
     * @return 构建器
     */
    public KnowledgeClientBuilder neo4j(String uri, String username, String password) {
        config.setNeo4jUri(uri);
        config.setNeo4jUsername(username);
        config.setNeo4jPassword(password);
        return this;
    }

    /**
     * 配置 Neo4j 数据库名称
     *
     * @param database 数据库名称
     * @return 构建器
     */
    public KnowledgeClientBuilder database(String database) {
        config.setNeo4jDatabase(database);
        return this;
    }

    /**
     * 配置数据来源标识
     *
     * @param producer 来源标识 (如: gravitino, datapillar-job, excel)
     * @return 构建器
     */
    public KnowledgeClientBuilder producer(String producer) {
        config.setProducer(producer);
        return this;
    }

    /**
     * 配置是否启用异步模式
     *
     * @param async 是否异步
     * @return 构建器
     */
    public KnowledgeClientBuilder async(boolean async) {
        config.setAsync(async);
        return this;
    }

    /**
     * 配置批量写入大小
     *
     * @param batchSize 批量大小
     * @return 构建器
     */
    public KnowledgeClientBuilder batchSize(int batchSize) {
        config.setBatchSize(batchSize);
        return this;
    }

    /**
     * 配置刷新间隔
     *
     * @param flushInterval 刷新间隔
     * @return 构建器
     */
    public KnowledgeClientBuilder flushInterval(Duration flushInterval) {
        config.setFlushInterval(flushInterval);
        return this;
    }

    /**
     * 配置连接池最大连接数
     *
     * @param maxConnectionPoolSize 最大连接数
     * @return 构建器
     */
    public KnowledgeClientBuilder maxConnectionPoolSize(int maxConnectionPoolSize) {
        config.setMaxConnectionPoolSize(maxConnectionPoolSize);
        return this;
    }

    /**
     * 配置优雅关闭超时
     *
     * @param shutdownTimeout 关闭超时
     * @return 构建器
     */
    public KnowledgeClientBuilder shutdownTimeout(Duration shutdownTimeout) {
        config.setShutdownTimeout(shutdownTimeout);
        return this;
    }

    /**
     * 配置死信队列
     *
     * @param enabled   是否启用
     * @param directory 存储目录（为空则使用内存队列）
     * @return 构建器
     */
    public KnowledgeClientBuilder deadLetterQueue(boolean enabled, String directory) {
        config.setDlqEnabled(enabled);
        config.setDlqDirectory(directory);
        return this;
    }

    /**
     * 配置内存死信队列最大容量
     *
     * @param maxSize 最大容量
     * @return 构建器
     */
    public KnowledgeClientBuilder dlqMaxSize(int maxSize) {
        config.setDlqMaxSize(maxSize);
        return this;
    }

    // ==================== P1 特性 ====================

    /**
     * 配置熔断器
     *
     * @param enabled              是否启用
     * @param failureRateThreshold 失败率阈值（0-100）
     * @return 构建器
     */
    public KnowledgeClientBuilder circuitBreaker(boolean enabled, int failureRateThreshold) {
        config.setCircuitBreakerEnabled(enabled);
        config.setCircuitBreakerFailureRateThreshold(failureRateThreshold);
        return this;
    }

    /**
     * 启用链路追踪
     *
     * @return 构建器
     */
    public KnowledgeClientBuilder enableTracing() {
        config.setTracingEnabled(true);
        return this;
    }

    /**
     * 配置数据校验
     *
     * @param enabled 是否启用
     * @return 构建器
     */
    public KnowledgeClientBuilder validation(boolean enabled) {
        config.setValidationEnabled(enabled);
        return this;
    }

    // ==================== P2 特性 ====================

    /**
     * 配置限流
     *
     * @param enabled        是否启用
     * @param limitPerSecond 每秒允许的请求数
     * @return 构建器
     */
    public KnowledgeClientBuilder rateLimit(boolean enabled, int limitPerSecond) {
        config.setRateLimitEnabled(enabled);
        config.setRateLimitPerSecond(limitPerSecond);
        return this;
    }

    /**
     * 配置租户
     *
     * @param tenantId 租户 ID
     * @return 构建器
     */
    public KnowledgeClientBuilder tenant(String tenantId) {
        config.setTenantId(tenantId);
        return this;
    }

    /**
     * 配置幂等性
     *
     * @param enabled    是否启用
     * @param ttlSeconds TTL（秒）
     * @return 构建器
     */
    public KnowledgeClientBuilder idempotent(boolean enabled, int ttlSeconds) {
        config.setIdempotentEnabled(enabled);
        config.setIdempotentTtlSeconds(ttlSeconds);
        return this;
    }

    /**
     * 构建客户端实例
     *
     * @return 知识库客户端
     */
    public KnowledgeClient build() {
        validate();
        return new DefaultKnowledgeClient(config);
    }

    private void validate() {
        if (config.getNeo4jUri() == null || config.getNeo4jUri().isBlank()) {
            throw new IllegalArgumentException("Neo4j URI 不能为空");
        }
        if (config.getNeo4jUsername() == null || config.getNeo4jUsername().isBlank()) {
            throw new IllegalArgumentException("Neo4j 用户名不能为空");
        }
        if (config.getNeo4jPassword() == null) {
            throw new IllegalArgumentException("Neo4j 密码不能为空");
        }
        if (config.getProducer() == null || config.getProducer().isBlank()) {
            throw new IllegalArgumentException("数据来源标识 (producer) 不能为空");
        }
    }

}
