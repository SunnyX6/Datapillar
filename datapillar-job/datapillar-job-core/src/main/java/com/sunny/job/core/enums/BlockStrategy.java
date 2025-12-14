package com.sunny.job.core.enums;

/**
 * 阻塞策略
 * <p>
 * 当任务触发时，已有实例在运行中的处理策略
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public enum BlockStrategy {

    DISCARD(1, "丢弃后续"),
    COVER(2, "覆盖之前"),
    PARALLEL(3, "并行执行");

    private final int code;
    private final String desc;

    BlockStrategy(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static BlockStrategy of(int code) {
        for (BlockStrategy strategy : values()) {
            if (strategy.code == code) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("未知的阻塞策略: " + code);
    }
}
