package com.sunny.job.core.strategy.retry;

/**
 * 重试退避策略
 * <p>
 * 计算重试间隔时间
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@FunctionalInterface
public interface BackoffStrategy {

    /**
     * 计算下次重试的延迟时间
     *
     * @param retryCount    当前重试次数（从 1 开始）
     * @param baseInterval  基础间隔（毫秒）
     * @return 延迟时间（毫秒）
     */
    long calculateDelay(int retryCount, long baseInterval);

    /**
     * 固定间隔策略
     * <p>
     * 每次重试间隔相同
     */
    BackoffStrategy FIXED = (retryCount, baseInterval) -> baseInterval;

    /**
     * 线性增长策略
     * <p>
     * 间隔 = baseInterval * retryCount
     */
    BackoffStrategy LINEAR = (retryCount, baseInterval) -> baseInterval * retryCount;

    /**
     * 指数退避策略
     * <p>
     * 间隔 = baseInterval * 2^(retryCount-1)
     */
    BackoffStrategy EXPONENTIAL = (retryCount, baseInterval) -> {
        long delay = baseInterval * (1L << (retryCount - 1));
        // 防止溢出
        return delay > 0 ? delay : Long.MAX_VALUE;
    };

    /**
     * 带抖动的指数退避策略
     * <p>
     * 间隔 = baseInterval * 2^(retryCount-1) * (0.5 ~ 1.5)
     */
    BackoffStrategy EXPONENTIAL_JITTER = (retryCount, baseInterval) -> {
        long baseDelay = baseInterval * (1L << (retryCount - 1));
        if (baseDelay <= 0) {
            return Long.MAX_VALUE;
        }
        // 添加 50% 的随机抖动
        double jitter = 0.5 + Math.random();
        return (long) (baseDelay * jitter);
    };

    /**
     * 带上限的指数退避策略
     *
     * @param maxDelay 最大延迟时间（毫秒）
     * @return 退避策略
     */
    static BackoffStrategy exponentialWithMax(long maxDelay) {
        return (retryCount, baseInterval) -> {
            long delay = EXPONENTIAL.calculateDelay(retryCount, baseInterval);
            return Math.min(delay, maxDelay);
        };
    }

    /**
     * 带上限和抖动的指数退避策略
     *
     * @param maxDelay 最大延迟时间（毫秒）
     * @return 退避策略
     */
    static BackoffStrategy exponentialJitterWithMax(long maxDelay) {
        return (retryCount, baseInterval) -> {
            long delay = EXPONENTIAL_JITTER.calculateDelay(retryCount, baseInterval);
            return Math.min(delay, maxDelay);
        };
    }
}
