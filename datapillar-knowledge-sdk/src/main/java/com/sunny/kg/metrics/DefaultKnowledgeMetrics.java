package com.sunny.kg.metrics;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntSupplier;

/**
 * 默认指标实现（线程安全）
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class DefaultKnowledgeMetrics implements KnowledgeMetrics {

    private final LongAdder emitTotal = new LongAdder();
    private final LongAdder emitSuccess = new LongAdder();
    private final LongAdder emitFailed = new LongAdder();
    private final LongAdder totalLatencyMs = new LongAdder();
    private final AtomicLong maxLatencyMs = new AtomicLong(0);

    private final IntSupplier queueSizeSupplier;
    private final IntSupplier dlqSizeSupplier;
    private final IntSupplier activeConnectionsSupplier;

    public DefaultKnowledgeMetrics(
            IntSupplier queueSizeSupplier,
            IntSupplier dlqSizeSupplier,
            IntSupplier activeConnectionsSupplier) {
        this.queueSizeSupplier = queueSizeSupplier;
        this.dlqSizeSupplier = dlqSizeSupplier;
        this.activeConnectionsSupplier = activeConnectionsSupplier;
    }

    /**
     * 记录成功
     */
    public void recordSuccess(long latencyMs) {
        emitTotal.increment();
        emitSuccess.increment();
        totalLatencyMs.add(latencyMs);
        updateMax(latencyMs);
    }

    /**
     * 记录失败
     */
    public void recordFailure(long latencyMs) {
        emitTotal.increment();
        emitFailed.increment();
        totalLatencyMs.add(latencyMs);
        updateMax(latencyMs);
    }

    private void updateMax(long latencyMs) {
        long current;
        do {
            current = maxLatencyMs.get();
            if (latencyMs <= current) {
                return;
            }
        } while (!maxLatencyMs.compareAndSet(current, latencyMs));
    }

    @Override
    public long getEmitTotal() {
        return emitTotal.sum();
    }

    @Override
    public long getEmitSuccess() {
        return emitSuccess.sum();
    }

    @Override
    public long getEmitFailed() {
        return emitFailed.sum();
    }

    @Override
    public double getAvgLatencyMs() {
        long total = emitTotal.sum();
        return total == 0 ? 0 : (double) totalLatencyMs.sum() / total;
    }

    @Override
    public long getMaxLatencyMs() {
        return maxLatencyMs.get();
    }

    @Override
    public int getQueueSize() {
        return queueSizeSupplier.getAsInt();
    }

    @Override
    public int getDlqSize() {
        return dlqSizeSupplier.getAsInt();
    }

    @Override
    public int getActiveConnections() {
        return activeConnectionsSupplier.getAsInt();
    }

    @Override
    public void reset() {
        emitTotal.reset();
        emitSuccess.reset();
        emitFailed.reset();
        totalLatencyMs.reset();
        maxLatencyMs.set(0);
    }

    @Override
    public MetricsSnapshot snapshot() {
        return MetricsSnapshot.builder()
                .timestamp(Instant.now())
                .emitTotal(getEmitTotal())
                .emitSuccess(getEmitSuccess())
                .emitFailed(getEmitFailed())
                .avgLatencyMs(getAvgLatencyMs())
                .maxLatencyMs(getMaxLatencyMs())
                .queueSize(getQueueSize())
                .dlqSize(getDlqSize())
                .activeConnections(getActiveConnections())
                .build();
    }

}
