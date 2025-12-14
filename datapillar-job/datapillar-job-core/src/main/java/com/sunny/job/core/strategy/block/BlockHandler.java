package com.sunny.job.core.strategy.block;

import com.sunny.job.core.enums.BlockStrategy;

/**
 * 阻塞策略处理器接口
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@FunctionalInterface
public interface BlockHandler {

    /**
     * 处理阻塞
     *
     * @param context 阻塞上下文
     * @return 处理结果
     */
    BlockResult handle(BlockContext context);

    /**
     * 阻塞上下文
     */
    record BlockContext(
            long jobId,
            Long runningInstanceId,
            boolean hasRunningInstance
    ) {
        public static BlockContext of(long jobId, Long runningInstanceId) {
            return new BlockContext(jobId, runningInstanceId, runningInstanceId != null);
        }

        public static BlockContext noRunning(long jobId) {
            return new BlockContext(jobId, null, false);
        }
    }

    /**
     * 获取阻塞策略处理器
     */
    static BlockHandler of(BlockStrategy strategy) {
        return switch (strategy) {
            case DISCARD -> BlockHandlers.DISCARD;
            case COVER -> BlockHandlers.COVER;
            case PARALLEL -> BlockHandlers.PARALLEL;
        };
    }
}
