package com.sunny.datapillar.auth.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 认证模块错误码枚举
 */
@Getter
@AllArgsConstructor
public enum AuthErrorCode {

    // ========== 成功码 ==========
    SUCCESS("OK", "操作成功"),

    // ========== 通用错误 ==========
    ERROR("ERROR", "操作失败"),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数验证失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误"),

    // ========== 认证错误 ==========
    UNAUTHORIZED("UNAUTHORIZED", "未授权访问"),
    FORBIDDEN("FORBIDDEN", "无权限访问"),
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在"),
    USER_DISABLED("USER_DISABLED", "用户已被禁用"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "用户名或密码错误"),

    // ========== Token 错误 ==========
    TOKEN_INVALID("TOKEN_INVALID", "Token无效: %s"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token已过期"),
    TOKEN_REVOKED("TOKEN_REVOKED", "Token已被撤销，请重新登录"),
    TOKEN_TYPE_ERROR("TOKEN_TYPE_ERROR", "Token类型错误"),
    REFRESH_TOKEN_FAILED("REFRESH_TOKEN_FAILED", "Token刷新失败: %s");

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
