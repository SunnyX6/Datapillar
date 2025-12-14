package com.sunny.job.core.strategy.block;

/**
 * 阻塞策略处理器实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class BlockHandlers {

    private BlockHandlers() {
    }

    /**
     * 丢弃策略：如果有运行中的实例，丢弃本次调度
     */
    public static final BlockHandler DISCARD = context -> {
        if (context.hasRunningInstance()) {
            return BlockResult.discard("任务正在运行中，丢弃本次调度");
        }
        return BlockResult.proceed();
    };

    /**
     * 覆盖策略：如果有运行中的实例，取消之前的实例
     */
    public static final BlockHandler COVER = context -> {
        if (context.hasRunningInstance()) {
            return BlockResult.cover(context.runningInstanceId());
        }
        return BlockResult.proceed();
    };

    /**
     * 并行策略：允许并行执行
     */
    public static final BlockHandler PARALLEL = context -> BlockResult.proceed();
}
