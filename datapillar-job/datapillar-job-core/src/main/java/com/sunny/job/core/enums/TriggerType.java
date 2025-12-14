package com.sunny.job.core.enums;

/**
 * 触发类型
 * <p>
 * 定义任务/工作流的触发方式
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public enum TriggerType {

    CRON(1, "CRON表达式"),
    FIXED_RATE(2, "固定频率"),
    FIXED_DELAY(3, "固定延迟"),
    MANUAL(4, "手动触发"),
    API(5, "API触发"),
    RETRY(6, "重试触发");

    private final int code;
    private final String desc;

    TriggerType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static TriggerType of(int code) {
        for (TriggerType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的触发类型: " + code);
    }
}
