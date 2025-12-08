package com.sunny.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统级错误码枚举
 *
 * @author sunny
 * @since 2024-11-08
 */
@Getter
@AllArgsConstructor
public enum GlobalSystemCode {

    // ========== 成功码 ==========
    SUCCESS("OK", "操作成功"),

    // ========== 通用错误 ==========
    ERROR("ERROR", "操作失败"),
    RESOURCE_NOT_FOUND("NOT_FOUND", "资源不存在"),
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", "资源已存在"),
    FORBIDDEN("FORBIDDEN", "无权限访问"),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数验证失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "服务器内部错误: %s"),

    // ========== 用户模块 ==========
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在: userId=%s"),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "用户名已存在: %s"),
    USERNAME_IN_USE("USERNAME_IN_USE", "用户名已被其他用户使用: %s"),
    PASSWORD_MISMATCH("PASSWORD_MISMATCH", "新密码和确认密码不一致"),
    CURRENT_PASSWORD_INCORRECT("CURRENT_PASSWORD_INCORRECT", "当前密码不正确"),

    // ========== 角色模块 ==========
    ROLE_NOT_FOUND("ROLE_NOT_FOUND", "角色不存在: roleId=%s"),
    ROLE_ALREADY_EXISTS("ROLE_ALREADY_EXISTS", "角色代码已存在: %s"),
    ROLE_IN_USE("ROLE_IN_USE", "角色正在使用中，无法删除: roleId=%s"),

    // ========== 项目模块 ==========
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "项目不存在: projectId=%s"),
    PROJECT_ACCESS_DENIED("PROJECT_ACCESS_DENIED", "无权限访问该项目: projectId=%s"),

    // ========== SQL模块 ==========
    SQL_EXECUTION_ERROR("SQL_EXECUTION_ERROR", "SQL执行失败: %s"),
    DATASOURCE_NOT_CONFIGURED("DATASOURCE_NOT_CONFIGURED", "数据源未配置"),

    // ========== 工作流模块 ==========
    WORKFLOW_NOT_FOUND("WORKFLOW_NOT_FOUND", "工作流不存在: workflowId=%s"),
    WORKFLOW_EXECUTION_ERROR("WORKFLOW_EXECUTION_ERROR", "工作流执行失败: %s"),

    // ========== 安全模块 ==========
    UNAUTHORIZED("UNAUTHORIZED", "未授权访问"),
    TOKEN_INVALID("TOKEN_INVALID", "Token无效: %s"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token已过期"),
    TOKEN_REVOKED("TOKEN_REVOKED", "Token已被撤销，请重新登录"),
    TOKEN_TYPE_ERROR("TOKEN_TYPE_ERROR", "Token类型错误"),
    USER_NOT_LOGGED_IN("USER_NOT_LOGGED_IN", "用户未登录"),
    USER_DISABLED("USER_DISABLED", "用户已被禁用"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "用户名或密码错误"),
    REFRESH_TOKEN_FAILED("REFRESH_TOKEN_FAILED", "Token刷新失败: %s"),

    // ========== 网关模块 ==========
    GATEWAY_SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "服务暂时不可用，请稍后重试"),
    GATEWAY_TIMEOUT("GATEWAY_TIMEOUT", "服务响应超时，请稍后重试"),
    GATEWAY_INTERNAL_ERROR("GATEWAY_INTERNAL_ERROR", "网关内部错误"),
    GATEWAY_RATE_LIMITED("RATE_LIMITED", "请求过于频繁，请稍后重试");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误消息模板 (支持 String.format)
     */
    private final String messageTemplate;

    /**
     * 获取格式化后的错误消息
     *
     * @param args 消息参数
     * @return 格式化后的消息
     */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }
}
