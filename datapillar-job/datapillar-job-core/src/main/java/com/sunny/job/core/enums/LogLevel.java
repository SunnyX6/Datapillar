package com.sunny.job.core.enums;

/**
 * 日志级别
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public enum LogLevel {

    INFO(1, "信息"),
    WARN(2, "警告"),
    ERROR(3, "错误");

    private final int code;
    private final String desc;

    LogLevel(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static LogLevel of(int code) {
        for (LogLevel level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        throw new IllegalArgumentException("未知的日志级别: " + code);
    }
}
