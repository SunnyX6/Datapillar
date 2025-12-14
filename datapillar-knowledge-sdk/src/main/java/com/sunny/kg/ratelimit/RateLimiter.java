package com.sunny.kg.ratelimit;

/**
 * 限流器接口
 *
 * @author Sunny
 * @since 2025-12-11
 */
public interface RateLimiter {

    /**
     * 尝试获取许可（非阻塞）
     *
     * @return 是否获取成功
     */
    boolean tryAcquire();

    /**
     * 尝试获取多个许可
     *
     * @param permits 许可数量
     * @return 是否获取成功
     */
    boolean tryAcquire(int permits);

    /**
     * 获取许可（阻塞）
     */
    void acquire();

    /**
     * 获取当前可用许可数
     */
    int availablePermits();

}
