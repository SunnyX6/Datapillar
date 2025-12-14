package com.sunny.kg.health;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 健康检查结果
 *
 * @author Sunny
 * @since 2025-12-11
 */
@Getter
@Builder
public class HealthCheckResult {

    private final Instant timestamp;
    private final KnowledgeHealth.Status status;
    private final Duration checkDuration;
    private final Map<String, Object> details;
    private final String errorMessage;

    public boolean isHealthy() {
        return status == KnowledgeHealth.Status.UP;
    }

    @Override
    public String toString() {
        return String.format("Health{status=%s, duration=%dms, error=%s}",
                status, checkDuration.toMillis(), errorMessage);
    }

}
