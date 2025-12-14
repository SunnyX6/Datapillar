package com.sunny.kg.retry;

import java.time.Duration;

/**
 * 重试策略
 *
 * @author Sunny
 * @since 2025-12-10
 */
public class RetryPolicy {

    private final int maxRetries;
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;

    private RetryPolicy(Builder builder) {
        this.maxRetries = builder.maxRetries;
        this.initialDelay = builder.initialDelay;
        this.multiplier = builder.multiplier;
        this.maxDelay = builder.maxDelay;
    }

    /**
     * 默认策略：3次重试，指数退避
     */
    public static RetryPolicy defaultPolicy() {
        return builder()
            .maxRetries(3)
            .initialDelay(Duration.ofMillis(100))
            .multiplier(2.0)
            .maxDelay(Duration.ofSeconds(5))
            .build();
    }

    /**
     * 不重试
     */
    public static RetryPolicy noRetry() {
        return builder().maxRetries(0).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    /**
     * 计算第 n 次重试的延迟时间
     */
    public Duration getDelayForAttempt(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        long delayMs = (long) (initialDelay.toMillis() * Math.pow(multiplier, attempt - 1));
        return Duration.ofMillis(Math.min(delayMs, maxDelay.toMillis()));
    }

    public static class Builder {
        private int maxRetries = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private double multiplier = 2.0;
        private Duration maxDelay = Duration.ofSeconds(5);

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }

}
