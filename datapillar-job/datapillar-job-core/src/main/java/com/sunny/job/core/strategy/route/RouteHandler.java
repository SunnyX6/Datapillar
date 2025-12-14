package com.sunny.job.core.strategy.route;

import com.sunny.job.core.enums.RouteStrategy;

/**
 * 路由策略处理器接口
 * <p>
 * 用于选择目标 Worker 执行任务
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@FunctionalInterface
public interface RouteHandler {

    /**
     * 执行路由选择
     *
     * @param context 路由上下文
     * @return 路由结果
     */
    RouteResult route(RouteContext context);

    /**
     * 获取路由策略处理器
     *
     * @param strategy 路由策略
     * @return 对应的处理器
     */
    static RouteHandler of(RouteStrategy strategy) {
        return switch (strategy) {
            case FIRST -> RouteHandlers.FIRST;
            case ROUND_ROBIN -> RouteHandlers.ROUND_ROBIN;
            case RANDOM -> RouteHandlers.RANDOM;
            case CONSISTENT_HASH -> RouteHandlers.CONSISTENT_HASH;
            case LEAST_BUSY -> RouteHandlers.LEAST_BUSY;
            case FAILOVER -> RouteHandlers.FAILOVER;
            case SHARDING -> RouteHandlers.SHARDING;
        };
    }
}
