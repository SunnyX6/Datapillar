package com.sunny.job.core.common;

/**
 * 常量定义
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class Constants {

    private Constants() {
    }

    // ==================== 系统常量 ====================

    /**
     * 默认命名空间
     */
    public static final String DEFAULT_NAMESPACE = "default";

    /**
     * 系统时区
     */
    public static final String SYSTEM_TIMEZONE = "Asia/Shanghai";

    // ==================== 调度常量 ====================

    /**
     * 默认触发超时时间 (毫秒)
     */
    public static final long DEFAULT_TRIGGER_TIMEOUT_MS = 60_000L;

    /**
     * 默认执行超时时间 (秒)
     */
    public static final int DEFAULT_EXECUTOR_TIMEOUT_SECONDS = 0;

    /**
     * 默认重试次数
     */
    public static final int DEFAULT_RETRY_TIMES = 0;

    /**
     * 默认重试间隔 (秒)
     */
    public static final int DEFAULT_RETRY_INTERVAL_SECONDS = 10;

    /**
     * 时间轮 tick 间隔 (毫秒)
     */
    public static final long TIMER_TICK_DURATION_MS = 100L;

    /**
     * 时间轮 槽位数量
     */
    public static final int TIMER_TICKS_PER_WHEEL = 512;

    // ==================== 租约常量 ====================

    /**
     * 默认租约 TTL (毫秒)
     */
    public static final long DEFAULT_LEASE_TTL_MS = 30_000L;

    /**
     * 租约续期间隔 (毫秒)，建议为 TTL 的 1/3
     */
    public static final long LEASE_RENEW_INTERVAL_MS = 10_000L;

    // ==================== 心跳常量 ====================

    /**
     * 心跳间隔 (毫秒)
     */
    public static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    /**
     * 心跳超时时间 (毫秒)
     */
    public static final long HEARTBEAT_TIMEOUT_MS = 30_000L;

    // ==================== 线程池常量 ====================

    /**
     * 调度线程池核心线程数
     */
    public static final int SCHEDULER_CORE_POOL_SIZE = 4;

    /**
     * 调度线程池最大线程数
     */
    public static final int SCHEDULER_MAX_POOL_SIZE = 16;

    /**
     * 执行线程池核心线程数
     */
    public static final int EXECUTOR_CORE_POOL_SIZE = 8;

    /**
     * 执行线程池最大线程数
     */
    public static final int EXECUTOR_MAX_POOL_SIZE = 64;

    /**
     * 线程池队列容量
     */
    public static final int THREAD_POOL_QUEUE_CAPACITY = 1024;

    /**
     * 线程空闲时间 (秒)
     */
    public static final long THREAD_KEEP_ALIVE_SECONDS = 60L;

    // ==================== gRPC 常量 ====================

    /**
     * gRPC 默认端口
     */
    public static final int GRPC_DEFAULT_PORT = 9999;

    /**
     * gRPC 连接超时 (毫秒)
     */
    public static final long GRPC_CONNECT_TIMEOUT_MS = 5_000L;

    /**
     * gRPC 请求超时 (毫秒)
     */
    public static final long GRPC_REQUEST_TIMEOUT_MS = 30_000L;

    // ==================== DAG 常量 ====================

    /**
     * DAG 最大节点数
     */
    public static final int DAG_MAX_NODES = 1000;

    /**
     * DAG 最大深度
     */
    public static final int DAG_MAX_DEPTH = 100;
}
