package com.sunny.job.core.split;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认 Split 大小计算器
 * <p>
 * 根据 Worker 资源（CPU、内存、负载）动态计算 splitSize
 * <p>
 * 算法：
 * <pre>
 * goalSize = baseFactor * availableCores * memoryFactor * loadFactor
 * splitSize = max(minSplitSize, min(maxSplitSize, goalSize))
 * </pre>
 *
 * @author Sunny
 */
public class DefaultSplitCalculator implements SplitCalculator {

    private static final Logger log = LoggerFactory.getLogger(DefaultSplitCalculator.class);

    /**
     * 最小分片大小（默认 1）
     */
    private final long minSplitSize;

    /**
     * 最大分片大小（默认 100）
     */
    private final long maxSplitSize;

    /**
     * 基础因子（每核心可处理的基础任务数）
     */
    private final int baseFactor;

    public DefaultSplitCalculator() {
        this(1, 100, 10);
    }

    public DefaultSplitCalculator(long minSplitSize, long maxSplitSize, int baseFactor) {
        this.minSplitSize = minSplitSize;
        this.maxSplitSize = maxSplitSize;
        this.baseFactor = baseFactor;
    }

    @Override
    public long calculate() {
        // 获取可用 CPU 核心数
        int availableCores = Runtime.getRuntime().availableProcessors();

        // 计算内存因子（可用内存 / 总内存）
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryFactor = Math.max(0.1, 1.0 - (double) usedMemory / maxMemory);

        // 计算负载因子（简化实现，实际可接入系统负载）
        double loadFactor = calculateLoadFactor();

        // 计算目标分片大小
        long goalSize = (long) (baseFactor * availableCores * memoryFactor * loadFactor);

        // 应用边界约束
        long splitSize = Math.max(minSplitSize, Math.min(maxSplitSize, goalSize));

        log.debug("计算 splitSize: cores={}, memoryFactor={:.2f}, loadFactor={:.2f}, goalSize={}, splitSize={}",
                availableCores, memoryFactor, loadFactor, goalSize, splitSize);

        return splitSize;
    }

    /**
     * 计算负载因子
     * <p>
     * 简化实现：根据 JVM 线程数估算
     * 实际场景可接入系统负载（如 /proc/loadavg）
     */
    private double calculateLoadFactor() {
        int activeThreads = Thread.activeCount();
        int availableCores = Runtime.getRuntime().availableProcessors();

        // 线程数 / 核心数 > 2 时，降低分片大小
        double ratio = (double) activeThreads / availableCores;
        if (ratio > 4) {
            return 0.25;
        } else if (ratio > 2) {
            return 0.5;
        } else if (ratio > 1) {
            return 0.75;
        } else {
            return 1.0;
        }
    }

    @Override
    public long getMinSplitSize() {
        return minSplitSize;
    }

    @Override
    public long getMaxSplitSize() {
        return maxSplitSize;
    }
}
