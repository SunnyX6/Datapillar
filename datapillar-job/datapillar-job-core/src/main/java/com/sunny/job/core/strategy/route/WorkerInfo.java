package com.sunny.job.core.strategy.route;

/**
 * Worker 节点信息
 * <p>
 * 用于路由策略选择目标 Worker
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public record WorkerInfo(
        String address,
        int maxConcurrency,
        int currentRunning,
        long lastHeartbeat
) {

    /**
     * 创建 Worker 信息
     *
     * @param address Worker 地址 (ip:port)
     */
    public static WorkerInfo of(String address) {
        return new WorkerInfo(address, 0, 0, System.currentTimeMillis());
    }

    /**
     * 创建带负载信息的 Worker
     *
     * @param address        Worker 地址
     * @param maxConcurrency 最大并发数
     * @param currentRunning 当前运行任务数
     */
    public static WorkerInfo of(String address, int maxConcurrency, int currentRunning) {
        return new WorkerInfo(address, maxConcurrency, currentRunning, System.currentTimeMillis());
    }

    /**
     * 获取可用容量
     */
    public int availableCapacity() {
        return Math.max(0, maxConcurrency - currentRunning);
    }

    /**
     * 是否有可用容量
     */
    public boolean hasCapacity() {
        return maxConcurrency <= 0 || currentRunning < maxConcurrency;
    }

    /**
     * 是否存活（心跳未超时）
     *
     * @param timeoutMs 超时时间（毫秒）
     */
    public boolean isAlive(long timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeat < timeoutMs;
    }
}
