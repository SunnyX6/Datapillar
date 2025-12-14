package com.sunny.kg.metrics;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 指标快照
 *
 * @author Sunny
 * @since 2025-12-11
 */
@Getter
@Builder
public class MetricsSnapshot {

    private final Instant timestamp;
    private final long emitTotal;
    private final long emitSuccess;
    private final long emitFailed;
    private final double avgLatencyMs;
    private final long maxLatencyMs;
    private final int queueSize;
    private final int dlqSize;
    private final int activeConnections;

    @Override
    public String toString() {
        return String.format(
            "Metrics{total=%d, success=%d, failed=%d, avgLatency=%.2fms, maxLatency=%dms, queue=%d, dlq=%d, connections=%d}",
            emitTotal, emitSuccess, emitFailed, avgLatencyMs, maxLatencyMs, queueSize, dlqSize, activeConnections
        );
    }

}
