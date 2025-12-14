package com.sunny.job.core.split;

/**
 * Split 大小计算器
 * <p>
 * Worker 根据自身资源（CPU、内存、负载）计算合适的 splitSize
 * <p>
 * 算法参考 Hadoop：
 * <pre>
 * goalSize = 根据 Worker 资源计算
 * splitSize = max(minSplitSize, min(maxSplitSize, goalSize))
 * </pre>
 *
 * @author Sunny
 */
public interface SplitCalculator {

    /**
     * 计算分片大小
     *
     * @return 分片大小
     */
    long calculate();

    /**
     * 获取最小分片大小
     */
    long getMinSplitSize();

    /**
     * 获取最大分片大小
     */
    long getMaxSplitSize();
}
