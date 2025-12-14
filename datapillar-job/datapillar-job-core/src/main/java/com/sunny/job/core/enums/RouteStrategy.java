package com.sunny.job.core.enums;

/**
 * 路由策略
 * <p>
 * Dispatcher 分发任务时选择 Worker 的策略
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public enum RouteStrategy {

    FIRST(1, "第一个可用"),
    ROUND_ROBIN(2, "轮询"),
    RANDOM(3, "随机"),
    CONSISTENT_HASH(4, "一致性哈希"),
    LEAST_BUSY(5, "最空闲"),
    FAILOVER(6, "故障转移"),
    SHARDING(7, "分片广播");

    private final int code;
    private final String desc;

    RouteStrategy(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 是否为分片广播模式（发送给所有 Worker）
     */
    public boolean isBroadcast() {
        return this == SHARDING;
    }

    public static RouteStrategy of(int code) {
        for (RouteStrategy strategy : values()) {
            if (strategy.code == code) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("未知的路由策略: " + code);
    }
}
