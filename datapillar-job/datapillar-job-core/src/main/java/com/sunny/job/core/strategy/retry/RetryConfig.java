package com.sunny.job.core.strategy.retry;

/**
 * 重试配置
 * <p>
 * 封装重试相关的配置参数
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public record RetryConfig(
        int maxRetryTimes,
        long baseIntervalMs,
        long maxIntervalMs,
        BackoffStrategy backoffStrategy
) {

    /**
     * 默认基础间隔（10 秒）
     */
    private static final long DEFAULT_BASE_INTERVAL_MS = 10_000L;

    /**
     * 默认最大间隔（10 分钟）
     */
    private static final long DEFAULT_MAX_INTERVAL_MS = 600_000L;

    /**
     * 创建固定间隔重试配置
     *
     * @param maxRetryTimes 最大重试次数
     * @param intervalMs    重试间隔（毫秒）
     */
    public static RetryConfig fixed(int maxRetryTimes, long intervalMs) {
        return new RetryConfig(
                maxRetryTimes,
                intervalMs,
                intervalMs,
                BackoffStrategy.FIXED
        );
    }

    /**
     * 创建指数退避重试配置
     *
     * @param maxRetryTimes 最大重试次数
     * @param baseIntervalMs 基础间隔（毫秒）
     */
    public static RetryConfig exponential(int maxRetryTimes, long baseIntervalMs) {
        return new RetryConfig(
                maxRetryTimes,
                baseIntervalMs,
                DEFAULT_MAX_INTERVAL_MS,
                BackoffStrategy.exponentialWithMax(DEFAULT_MAX_INTERVAL_MS)
        );
    }

    /**
     * 创建指数退避重试配置（带抖动）
     *
     * @param maxRetryTimes  最大重试次数
     * @param baseIntervalMs 基础间隔（毫秒）
     * @param maxIntervalMs  最大间隔（毫秒）
     */
    public static RetryConfig exponentialJitter(int maxRetryTimes, long baseIntervalMs, long maxIntervalMs) {
        return new RetryConfig(
                maxRetryTimes,
                baseIntervalMs,
                maxIntervalMs,
                BackoffStrategy.exponentialJitterWithMax(maxIntervalMs)
        );
    }

    /**
     * 创建默认配置（指数退避 + 抖动）
     *
     * @param maxRetryTimes 最大重试次数
     */
    public static RetryConfig defaults(int maxRetryTimes) {
        return exponentialJitter(maxRetryTimes, DEFAULT_BASE_INTERVAL_MS, DEFAULT_MAX_INTERVAL_MS);
    }

    /**
     * 不重试
     */
    public static RetryConfig noRetry() {
        return new RetryConfig(0, 0, 0, BackoffStrategy.FIXED);
    }

    /**
     * 是否允许重试
     *
     * @param currentRetryCount 当前已重试次数
     */
    public boolean canRetry(int currentRetryCount) {
        return maxRetryTimes > 0 && currentRetryCount < maxRetryTimes;
    }

    /**
     * 计算下次重试的延迟时间
     *
     * @param currentRetryCount 当前已重试次数
     * @return 延迟时间（毫秒）
     */
    public long nextDelay(int currentRetryCount) {
        if (!canRetry(currentRetryCount)) {
            return -1;
        }
        long delay = backoffStrategy.calculateDelay(currentRetryCount + 1, baseIntervalMs);
        return Math.min(delay, maxIntervalMs);
    }
}
