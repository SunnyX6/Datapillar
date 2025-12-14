package com.sunny.gateway.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 网关错误码枚举
 */
@Getter
@AllArgsConstructor
public enum GatewayErrorCode {

    // ========== 成功码 ==========
    SUCCESS("OK", "操作成功"),

    // ========== 通用错误 ==========
    ERROR("ERROR", "操作失败"),
    RESOURCE_NOT_FOUND("NOT_FOUND", "资源不存在"),
    UNAUTHORIZED("UNAUTHORIZED", "未授权访问"),
    FORBIDDEN("FORBIDDEN", "无权限访问"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误"),

    // ========== 网关错误 ==========
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "服务暂时不可用，请稍后重试"),
    GATEWAY_TIMEOUT("GATEWAY_TIMEOUT", "服务响应超时，请稍后重试"),
    GATEWAY_INTERNAL_ERROR("GATEWAY_INTERNAL_ERROR", "网关内部错误"),
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁，请稍后重试");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误消息模板
     */
    private final String messageTemplate;

    /**
     * 获取格式化后的错误消息
     */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }
}
