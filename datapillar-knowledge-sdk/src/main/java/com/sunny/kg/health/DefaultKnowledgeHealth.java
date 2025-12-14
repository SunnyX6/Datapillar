package com.sunny.kg.health;

import com.sunny.kg.metrics.KnowledgeMetrics;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 默认健康检查实现
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class DefaultKnowledgeHealth implements KnowledgeHealth {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeHealth.class);

    private static final double FAILURE_RATE_THRESHOLD = 0.5;
    private static final int QUEUE_SIZE_THRESHOLD = 10000;

    private final Supplier<Driver> driverSupplier;
    private final KnowledgeMetrics metrics;
    private final Supplier<Boolean> closedSupplier;

    private final AtomicReference<HealthCheckResult> lastResult = new AtomicReference<>();

    public DefaultKnowledgeHealth(
            Supplier<Driver> driverSupplier,
            KnowledgeMetrics metrics,
            Supplier<Boolean> closedSupplier) {
        this.driverSupplier = driverSupplier;
        this.metrics = metrics;
        this.closedSupplier = closedSupplier;
    }

    @Override
    public boolean isHealthy() {
        return getStatus() == Status.UP;
    }

    @Override
    public Status getStatus() {
        if (closedSupplier.get()) {
            return Status.DOWN;
        }

        long total = metrics.getEmitTotal();
        if (total > 0) {
            double failureRate = (double) metrics.getEmitFailed() / total;
            if (failureRate > FAILURE_RATE_THRESHOLD) {
                return Status.DOWN;
            }
        }

        if (metrics.getQueueSize() > QUEUE_SIZE_THRESHOLD) {
            return Status.DEGRADED;
        }

        if (metrics.getDlqSize() > 0) {
            return Status.DEGRADED;
        }

        return Status.UP;
    }

    @Override
    public Map<String, Object> getDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", getStatus().name());
        details.put("closed", closedSupplier.get());
        details.put("queueSize", metrics.getQueueSize());
        details.put("dlqSize", metrics.getDlqSize());
        details.put("emitTotal", metrics.getEmitTotal());
        details.put("emitFailed", metrics.getEmitFailed());

        long total = metrics.getEmitTotal();
        if (total > 0) {
            details.put("failureRate", String.format("%.2f%%", (double) metrics.getEmitFailed() / total * 100));
        }

        HealthCheckResult last = lastResult.get();
        if (last != null) {
            details.put("lastCheckTime", last.getTimestamp().toString());
            details.put("lastCheckDurationMs", last.getCheckDuration().toMillis());
        }

        return details;
    }

    @Override
    public HealthCheckResult check() {
        Instant start = Instant.now();
        Status status;
        String errorMessage = null;
        Map<String, Object> details = new LinkedHashMap<>();

        try {
            if (closedSupplier.get()) {
                status = Status.DOWN;
                errorMessage = "Client is closed";
            } else {
                Driver driver = driverSupplier.get();
                if (driver != null) {
                    driver.verifyConnectivity();
                    details.put("neo4jConnected", true);
                }
                status = getStatus();
            }
        } catch (Exception e) {
            log.warn("健康检查失败", e);
            status = Status.DOWN;
            errorMessage = e.getMessage();
            details.put("neo4jConnected", false);
        }

        details.putAll(getDetails());

        HealthCheckResult result = HealthCheckResult.builder()
                .timestamp(start)
                .status(status)
                .checkDuration(Duration.between(start, Instant.now()))
                .details(details)
                .errorMessage(errorMessage)
                .build();

        lastResult.set(result);
        return result;
    }

}
