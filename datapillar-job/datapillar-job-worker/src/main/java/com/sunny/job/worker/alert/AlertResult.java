package com.sunny.job.worker.alert;

/**
 * 告警发送结果
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public record AlertResult(boolean success, String message) {

    public static AlertResult ok() {
        return new AlertResult(true, "发送成功");
    }

    public static AlertResult ok(String message) {
        return new AlertResult(true, message);
    }

    public static AlertResult fail(String message) {
        return new AlertResult(false, message);
    }
}
