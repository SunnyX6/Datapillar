package com.sunny.kg.circuitbreaker;

/**
 * 熔断器接口
 *
 * @author Sunny
 * @since 2025-12-11
 */
public interface CircuitBreaker {

    /**
     * 熔断器状态
     */
    enum State {
        CLOSED,      // 关闭（正常）
        OPEN,        // 打开（熔断中）
        HALF_OPEN    // 半开（尝试恢复）
    }

    /**
     * 尝试获取执行许可
     *
     * @return 是否允许执行
     */
    boolean tryAcquire();

    /**
     * 记录成功
     */
    void recordSuccess();

    /**
     * 记录失败
     */
    void recordFailure();

    /**
     * 获取当前状态
     */
    State getState();

    /**
     * 强制重置为关闭状态
     */
    void reset();

}
