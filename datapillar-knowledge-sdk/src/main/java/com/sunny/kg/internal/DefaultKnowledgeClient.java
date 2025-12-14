package com.sunny.kg.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sunny.kg.circuitbreaker.*;
import com.sunny.kg.client.KnowledgeClient;
import com.sunny.kg.client.KnowledgeClientConfig;
import com.sunny.kg.dlq.*;
import com.sunny.kg.health.DefaultKnowledgeHealth;
import com.sunny.kg.health.KnowledgeHealth;
import com.sunny.kg.idempotent.*;
import com.sunny.kg.internal.mapper.CypherStatement;
import com.sunny.kg.internal.mapper.KnowledgeMapper;
import com.sunny.kg.internal.transport.Neo4jTransport;
import com.sunny.kg.metrics.DefaultKnowledgeMetrics;
import com.sunny.kg.metrics.KnowledgeMetrics;
import com.sunny.kg.model.*;
import com.sunny.kg.ratelimit.*;
import com.sunny.kg.retry.RetryExecutor;
import com.sunny.kg.retry.RetryPolicy;
import com.sunny.kg.shutdown.GracefulShutdown;
import com.sunny.kg.spi.Interceptor;
import com.sunny.kg.spi.InterceptorChain;
import com.sunny.kg.spi.InterceptorContext;
import com.sunny.kg.tenant.TenantAwareInterceptor;
import com.sunny.kg.tenant.TenantContext;
import com.sunny.kg.tracing.*;
import com.sunny.kg.validation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 知识库客户端默认实现（整合所有 P0/P1/P2 特性）
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class DefaultKnowledgeClient implements KnowledgeClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeClient.class);

    private final KnowledgeClientConfig config;
    private final KnowledgeMapper mapper;
    private final Neo4jTransport transport;
    private final RetryExecutor retryExecutor;
    private final InterceptorChain interceptorChain;
    private final ObjectMapper objectMapper;

    // P0 特性
    private final DefaultKnowledgeMetrics metrics;
    private final DefaultKnowledgeHealth health;
    private final DeadLetterQueue dlq;
    private final GracefulShutdown gracefulShutdown;

    // P1 特性
    private final CircuitBreaker circuitBreaker;
    private final Tracer tracer;
    private final Validator validator;

    // P2 特性
    private final RateLimiter rateLimiter;
    private final IdempotentStore idempotentStore;
    private final Duration idempotentTtl;

    private volatile boolean closed = false;

    public DefaultKnowledgeClient(KnowledgeClientConfig config) {
        this.config = config;
        this.mapper = new KnowledgeMapper(config.getProducer());
        this.transport = new Neo4jTransport(config);
        this.retryExecutor = new RetryExecutor(RetryPolicy.defaultPolicy());
        this.interceptorChain = new InterceptorChain();
        this.gracefulShutdown = new GracefulShutdown();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // P0: 初始化 DLQ
        if (config.isDlqEnabled()) {
            if (config.getDlqDirectory() != null && !config.getDlqDirectory().isBlank()) {
                this.dlq = new FileDeadLetterQueue(config.getDlqDirectory());
            } else {
                this.dlq = new InMemoryDeadLetterQueue(config.getDlqMaxSize());
            }
        } else {
            this.dlq = null;
        }

        // P0: 初始化 Metrics
        this.metrics = new DefaultKnowledgeMetrics(
                transport::getQueueSize,
                () -> dlq != null ? dlq.size() : 0,
                transport::getActiveConnections
        );

        // P0: 初始化 Health
        this.health = new DefaultKnowledgeHealth(
                transport::getDriver,
                metrics,
                () -> closed
        );

        // P1: 初始化熔断器
        if (config.isCircuitBreakerEnabled()) {
            this.circuitBreaker = new DefaultCircuitBreaker(CircuitBreakerConfig.builder()
                    .failureRateThreshold(config.getCircuitBreakerFailureRateThreshold())
                    .slidingWindowSize(config.getCircuitBreakerSlidingWindowSize())
                    .waitDurationInOpenState(Duration.ofSeconds(config.getCircuitBreakerWaitDurationSeconds()))
                    .build());
        } else {
            this.circuitBreaker = null;
        }

        // P1: 初始化链路追踪
        this.tracer = config.isTracingEnabled() ? new LoggingTracer() : NoopTracer.INSTANCE;

        // P1: 初始化数据校验
        this.validator = new Validator(config.isValidationEnabled()
                ? ValidationConfig.defaultConfig()
                : ValidationConfig.disabled());

        // P2: 初始化限流器
        if (config.isRateLimitEnabled()) {
            this.rateLimiter = new SlidingWindowRateLimiter(RateLimiterConfig.builder()
                    .limitForPeriod(config.getRateLimitPerSecond())
                    .build());
        } else {
            this.rateLimiter = null;
        }

        // P2: 初始化幂等性存储
        if (config.isIdempotentEnabled()) {
            this.idempotentStore = new InMemoryIdempotentStore();
            this.idempotentTtl = Duration.ofSeconds(config.getIdempotentTtlSeconds());
        } else {
            this.idempotentStore = null;
            this.idempotentTtl = null;
        }

        // P2: 添加租户拦截器
        if (config.getTenantId() != null) {
            interceptorChain.addInterceptor(new TenantAwareInterceptor(config.getTenantId()));
        }

        log.info("KnowledgeClient 初始化完成, producer={}, circuitBreaker={}, tracing={}, validation={}, rateLimit={}, tenant={}, idempotent={}",
                config.getProducer(),
                config.isCircuitBreakerEnabled(),
                config.isTracingEnabled(),
                config.isValidationEnabled(),
                config.isRateLimitEnabled(),
                config.getTenantId(),
                config.isIdempotentEnabled());
    }

    public void addInterceptor(Interceptor interceptor) {
        interceptorChain.addInterceptor(interceptor);
    }

    // ==================== 表元数据 ====================

    @Override
    public void emitTable(TableMeta table) {
        validator.validate(table);
        String idempotentKey = config.isIdempotentEnabled() ? IdempotentKeyGenerator.generate(table) : null;

        execute("emitTable", table, idempotentKey, () -> {
            List<CypherStatement> statements = mapper.mapTable(table);
            transport.send(statements);
        });
    }

    @Override
    public void emitTables(List<TableMeta> tables) {
        for (TableMeta table : tables) {
            emitTable(table);
        }
    }

    // ==================== 目录和分层 ====================

    @Override
    public void emitCatalog(CatalogMeta catalog) {
        validator.validate(catalog);
        String idempotentKey = config.isIdempotentEnabled() ? IdempotentKeyGenerator.generate(catalog) : null;

        execute("emitCatalog", catalog, idempotentKey, () -> {
            List<CypherStatement> statements = mapper.mapCatalog(catalog);
            transport.send(statements);
        });
    }

    @Override
    public void emitSchema(SchemaMeta schema) {
        validator.validate(schema);
        String idempotentKey = config.isIdempotentEnabled() ? IdempotentKeyGenerator.generate(schema) : null;

        execute("emitSchema", schema, idempotentKey, () -> {
            List<CypherStatement> statements = mapper.mapSchema(schema);
            transport.send(statements);
        });
    }

    // ==================== 血缘 ====================

    @Override
    public void emitLineage(Lineage lineage) {
        validator.validate(lineage);
        String idempotentKey = config.isIdempotentEnabled() ? IdempotentKeyGenerator.generate(lineage) : null;

        execute("emitLineage", lineage, idempotentKey, () -> {
            List<CypherStatement> statements = mapper.mapLineage(lineage);
            transport.send(statements);
        });
    }

    @Override
    public void emitLineages(List<Lineage> lineages) {
        for (Lineage lineage : lineages) {
            emitLineage(lineage);
        }
    }

    @Override
    public void emitLineage(String sourceTable, String targetTable, String transformationType) {
        emitLineage(Lineage.builder()
                .sourceTable(sourceTable)
                .targetTable(targetTable)
                .transformationType(transformationType)
                .build());
    }

    // ==================== 指标 ====================

    @Override
    public void emitMetric(MetricMeta metric) {
        validator.validate(metric);
        String idempotentKey = config.isIdempotentEnabled() ? IdempotentKeyGenerator.generate(metric) : null;

        execute("emitMetric", metric, idempotentKey, () -> {
            List<CypherStatement> statements = mapper.mapMetric(metric);
            transport.send(statements);
        });
    }

    @Override
    public void emitMetrics(List<MetricMeta> metrics) {
        for (MetricMeta metric : metrics) {
            emitMetric(metric);
        }
    }

    // ==================== 质量规则 ====================

    @Override
    public void emitQualityRule(QualityRuleMeta rule) {
        validator.validate(rule);
        String idempotentKey = config.isIdempotentEnabled() ? IdempotentKeyGenerator.generate(rule) : null;

        execute("emitQualityRule", rule, idempotentKey, () -> {
            List<CypherStatement> statements = mapper.mapQualityRule(rule);
            transport.send(statements);
        });
    }

    @Override
    public void emitQualityRules(List<QualityRuleMeta> rules) {
        for (QualityRuleMeta rule : rules) {
            emitQualityRule(rule);
        }
    }

    // ==================== 通用操作 ====================

    @Override
    public void flush() {
        transport.flush();
    }

    @Override
    public void close() {
        close(config.getShutdownTimeout());
    }

    @Override
    public boolean close(Duration timeout) {
        if (closed) {
            return true;
        }

        boolean success = gracefulShutdown.shutdown(
                timeout,
                this::flush,
                transport.getExecutors(),
                this::doClose
        );

        closed = true;
        return success;
    }

    @Override
    public void closeNow() {
        if (closed) {
            return;
        }
        gracefulShutdown.shutdownNow(transport.getExecutors(), this::doClose);
        closed = true;
    }

    private void doClose() {
        transport.closeDriver();
        if (dlq != null) {
            dlq.close();
        }
        log.info("KnowledgeClient 已关闭, metrics: {}", metrics.snapshot());
    }

    // ==================== 可观测性 ====================

    @Override
    public KnowledgeMetrics metrics() {
        return metrics;
    }

    @Override
    public KnowledgeHealth health() {
        return health;
    }

    // ==================== 核心执行方法 ====================

    private void execute(String operation, Object payload, String idempotentKey, Runnable action) {
        // P2: 幂等性检查
        if (idempotentKey != null && idempotentStore != null) {
            if (!idempotentStore.checkAndMark(idempotentKey, idempotentTtl)) {
                log.debug("幂等性检查: 跳过重复写入, key={}", idempotentKey);
                return;
            }
        }

        // P2: 限流检查
        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            throw new RateLimitExceededException();
        }

        // P1: 熔断检查
        if (circuitBreaker != null && !circuitBreaker.tryAcquire()) {
            throw new CircuitBreakerOpenException();
        }

        // P1: 链路追踪
        try (Span span = tracer.startSpan("knowledge." + operation)) {
            span.setTag("operation", operation);

            String tenant = TenantContext.getTenant();
            if (tenant != null) {
                span.setTag("tenant", tenant);
            }

            InterceptorContext context = new InterceptorContext(operation, payload);
            long startTime = System.currentTimeMillis();

            if (!interceptorChain.applyBefore(context)) {
                return;
            }

            try {
                retryExecutor.execute(action, operation);

                // 成功
                context.setSuccess(true);
                long latency = System.currentTimeMillis() - startTime;
                metrics.recordSuccess(latency);

                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }

                span.setSuccess();

            } catch (Exception e) {
                // 失败
                long latency = System.currentTimeMillis() - startTime;
                context.setSuccess(false);
                metrics.recordFailure(latency);

                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }

                span.logError(e);
                interceptorChain.applyError(context, e);

                // 写入死信队列
                if (dlq != null) {
                    writeToDlq(operation, payload, e);
                }

                // 幂等性回滚
                if (idempotentKey != null && idempotentStore != null) {
                    idempotentStore.remove(idempotentKey);
                }

                throw e;

            } finally {
                context.setDurationMs(System.currentTimeMillis() - startTime);
                interceptorChain.applyAfter(context);
            }
        }
    }

    private void writeToDlq(String operation, Object payload, Exception e) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            DeadLetterRecord record = DeadLetterRecord.builder()
                    .operation(operation)
                    .payload(payloadJson)
                    .errorMessage(e.getMessage())
                    .errorType(e.getClass().getName())
                    .createdAt(Instant.now())
                    .build();
            dlq.write(record);
            log.debug("写入死信队列: operation={}, id={}", operation, record.getId());
        } catch (JsonProcessingException ex) {
            log.error("序列化失败，无法写入死信队列", ex);
        }
    }

}
