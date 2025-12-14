package com.sunny.kg.internal.transport;

import com.sunny.kg.client.KnowledgeClientConfig;
import com.sunny.kg.internal.mapper.CypherStatement;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

/**
 * Neo4j 异步传输层
 * <p>
 * 支持批量写入和定时刷新
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class Neo4jTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(Neo4jTransport.class);

    private final Driver driver;
    private final String database;
    private final int batchSize;
    private final boolean async;

    private final BlockingQueue<CypherStatement> buffer;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService executor;

    private volatile boolean closed = false;
    private volatile int activeConnections = 0;

    public Neo4jTransport(KnowledgeClientConfig config) {
        this.database = config.getNeo4jDatabase();
        this.batchSize = config.getBatchSize();
        this.async = config.isAsync();

        // 创建 Neo4j Driver
        this.driver = GraphDatabase.driver(
            config.getNeo4jUri(),
            AuthTokens.basic(config.getNeo4jUsername(), config.getNeo4jPassword()),
            Config.builder()
                .withMaxConnectionPoolSize(config.getMaxConnectionPoolSize())
                .withConnectionAcquisitionTimeout(
                    config.getConnectionAcquisitionTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
                )
                .build()
        );

        // 验证连接
        driver.verifyConnectivity();
        log.info("Neo4j 连接成功: {}", config.getNeo4jUri());

        if (async) {
            this.buffer = new LinkedBlockingQueue<>();
            this.executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "knowledge-sdk-writer");
                t.setDaemon(true);
                return t;
            });
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "knowledge-sdk-flusher");
                t.setDaemon(true);
                return t;
            });

            // 定时刷新
            long flushIntervalMs = config.getFlushInterval().toMillis();
            scheduler.scheduleAtFixedRate(
                this::flushInternal,
                flushIntervalMs,
                flushIntervalMs,
                TimeUnit.MILLISECONDS
            );
        } else {
            this.buffer = null;
            this.scheduler = null;
            this.executor = null;
        }
    }

    @Override
    public void send(CypherStatement statement) {
        if (closed) {
            throw new IllegalStateException("Transport 已关闭");
        }

        if (async) {
            buffer.offer(statement);
            if (buffer.size() >= batchSize) {
                executor.submit(this::flushInternal);
            }
        } else {
            executeImmediate(List.of(statement));
        }
    }

    @Override
    public void send(List<CypherStatement> statements) {
        if (closed) {
            throw new IllegalStateException("Transport 已关闭");
        }

        if (async) {
            buffer.addAll(statements);
            if (buffer.size() >= batchSize) {
                executor.submit(this::flushInternal);
            }
        } else {
            executeImmediate(statements);
        }
    }

    @Override
    public void flush() {
        if (async) {
            try {
                executor.submit(this::flushInternal).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("刷新被中断");
            } catch (ExecutionException e) {
                log.error("刷新失败", e.getCause());
            }
        }
    }

    private void flushInternal() {
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        List<CypherStatement> batch = new java.util.ArrayList<>();
        buffer.drainTo(batch, batchSize);

        if (!batch.isEmpty()) {
            executeImmediate(batch);
        }
    }

    private void executeImmediate(List<CypherStatement> statements) {
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                for (CypherStatement stmt : statements) {
                    tx.run(stmt.cypher(), stmt.params());
                }
                return null;
            });
            log.debug("成功写入 {} 条 Cypher 语句", statements.size());
        } catch (Exception e) {
            log.error("写入 Neo4j 失败", e);
            throw new RuntimeException("写入 Neo4j 失败", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        // 刷新剩余数据
        if (async) {
            flushInternal();

            if (scheduler != null) {
                scheduler.shutdown();
            }
            if (executor != null) {
                executor.shutdown();
            }
        }

        // 关闭 Driver
        closeDriver();
    }

    /**
     * 关闭 Neo4j Driver
     */
    public void closeDriver() {
        if (driver != null) {
            driver.close();
            log.info("Neo4j 连接已关闭");
        }
    }

    /**
     * 获取当前队列大小
     */
    public int getQueueSize() {
        return buffer != null ? buffer.size() : 0;
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnections() {
        return activeConnections;
    }

    /**
     * 获取 Driver（用于健康检查）
     */
    public Driver getDriver() {
        return driver;
    }

    /**
     * 获取线程池（用于优雅关闭）
     */
    public ExecutorService[] getExecutors() {
        if (async) {
            return new ExecutorService[]{executor, scheduler};
        }
        return new ExecutorService[0];
    }

}
