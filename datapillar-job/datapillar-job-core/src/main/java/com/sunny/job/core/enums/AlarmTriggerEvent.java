package com.sunny.job.core.enums;

/**
 * 告警触发事件
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public enum AlarmTriggerEvent {

    FAIL(1, "失败"),
    TIMEOUT(2, "超时"),
    SUCCESS(3, "成功");

    private final int code;
    private final String desc;

    AlarmTriggerEvent(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static AlarmTriggerEvent of(int code) {
        for (AlarmTriggerEvent event : values()) {
            if (event.code == code) {
                return event;
            }
        }
        throw new IllegalArgumentException("未知的告警触发事件: " + code);
    }
}
