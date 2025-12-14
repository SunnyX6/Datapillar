package com.sunny.job.core.strategy.route;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路由策略处理器实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class RouteHandlers {

    private RouteHandlers() {
    }

    /**
     * 轮询计数器
     */
    private static final AtomicLong ROUND_ROBIN_COUNTER = new AtomicLong(0);

    /**
     * FIRST 策略：选择第一个可用的 Worker
     */
    public static final RouteHandler FIRST = context -> {
        if (!context.hasWorkers()) {
            return RouteResult.noWorker();
        }
        return RouteResult.success(context.workers().getFirst());
    };

    /**
     * ROUND_ROBIN 策略：轮询选择 Worker
     */
    public static final RouteHandler ROUND_ROBIN = context -> {
        if (!context.hasWorkers()) {
            return RouteResult.noWorker();
        }

        List<WorkerInfo> workers = context.workers();
        int index = (int) (ROUND_ROBIN_COUNTER.getAndIncrement() % workers.size());
        // 处理计数器溢出后的负数情况
        if (index < 0) {
            index = index + workers.size();
        }
        return RouteResult.success(workers.get(index));
    };

    /**
     * RANDOM 策略：随机选择 Worker
     */
    public static final RouteHandler RANDOM = context -> {
        if (!context.hasWorkers()) {
            return RouteResult.noWorker();
        }

        List<WorkerInfo> workers = context.workers();
        int index = ThreadLocalRandom.current().nextInt(workers.size());
        return RouteResult.success(workers.get(index));
    };

    /**
     * CONSISTENT_HASH 策略：一致性哈希选择 Worker
     * <p>
     * 相同的 jobParams 或 jobId 总是路由到同一个 Worker
     */
    public static final RouteHandler CONSISTENT_HASH = context -> {
        if (!context.hasWorkers()) {
            return RouteResult.noWorker();
        }

        ConsistentHashRouter router = ConsistentHashRouter.create(context.workers());
        WorkerInfo selected = router.select(context.hashKey());

        if (selected == null) {
            return RouteResult.noWorker();
        }
        return RouteResult.success(selected);
    };

    /**
     * LEAST_BUSY 策略：选择最空闲的 Worker
     * <p>
     * 根据 (maxConcurrency - currentRunning) 选择可用容量最大的
     */
    public static final RouteHandler LEAST_BUSY = context -> {
        if (!context.hasWorkers()) {
            return RouteResult.noWorker();
        }

        WorkerInfo selected = context.workers().stream()
                .filter(WorkerInfo::hasCapacity)
                .max(Comparator.comparingInt(WorkerInfo::availableCapacity))
                .orElse(null);

        if (selected == null) {
            // 所有 Worker 都满载，退化为 ROUND_ROBIN
            return ROUND_ROBIN.route(context);
        }
        return RouteResult.success(selected);
    };

    /**
     * FAILOVER 策略：故障转移，返回排序后的第一个
     * <p>
     * 调用方在执行失败时应该按顺序尝试下一个 Worker
     */
    public static final RouteHandler FAILOVER = context -> {
        if (!context.hasWorkers()) {
            return RouteResult.noWorker();
        }

        // 按地址排序，保证顺序稳定
        List<WorkerInfo> sortedWorkers = context.workers().stream()
                .sorted(Comparator.comparing(WorkerInfo::address))
                .toList();

        return RouteResult.success(sortedWorkers.getFirst());
    };

    /**
     * SHARDING 策略：分片广播，返回所有 Worker
     * <p>
     * 任务会发送给所有 Worker 并行执行，每个 Worker 通过 shardIndex 处理不同的数据分片
     */
    public static final RouteHandler SHARDING = context -> {
        if (!context.hasWorkers()) {
            return RouteResult.noWorker();
        }
        return RouteResult.success(context.workers());
    };

    /**
     * 重置轮询计数器（主要用于测试）
     */
    public static void resetRoundRobinCounter() {
        ROUND_ROBIN_COUNTER.set(0);
    }
}
