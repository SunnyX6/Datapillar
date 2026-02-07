package com.sunny.datapillar.common.error;

import lombok.Getter;

/**
 * 统一错误码清单
 */
public enum ErrorCode {

    // ========== 成功码 ==========
    OK("OK", "操作成功", 200),

    // ========== 通用 ==========
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误", 500),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "服务不可用", 503),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数验证失败", 400),
    INVALID_ARGUMENT("INVALID_ARGUMENT", "参数错误", 400),
    DUPLICATE_KEY("DUPLICATE_KEY", "资源已存在", 409),
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", "资源已存在", 409),
    DUPLICATE_PARAM_KEY("DUPLICATE_PARAM_KEY", "参数键已存在", 409),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "资源不存在", 404),
    UNAUTHORIZED("UNAUTHORIZED", "未授权访问", 401),
    FORBIDDEN("FORBIDDEN", "无权限访问", 403),

    // ========== 用户/租户 ==========
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在: %s", 404),
    USER_DISABLED("USER_DISABLED", "用户已被禁用", 403),
    USER_NOT_LOGGED_IN("USER_NOT_LOGGED_IN", "用户未登录", 401),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "用户名已存在: %s", 409),
    USERNAME_IN_USE("USERNAME_IN_USE", "用户名已被其他用户使用: %s", 409),
    PASSWORD_MISMATCH("PASSWORD_MISMATCH", "新密码和确认密码不一致", 400),
    CURRENT_PASSWORD_INCORRECT("CURRENT_PASSWORD_INCORRECT", "当前密码不正确", 400),
    TENANT_NOT_FOUND("TENANT_NOT_FOUND", "租户不存在: %s", 401),
    TENANT_DISABLED("TENANT_DISABLED", "租户已被禁用: tenantId=%s", 403),
    TENANT_USER_DISABLED("TENANT_USER_DISABLED", "租户成员已被禁用: tenantId=%s,userId=%s", 403),

    // ========== 认证/登录 ==========
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "用户名或密码错误", 401),
    INVITE_REQUIRED("INVITE_REQUIRED", "首次入库必须使用邀请码", 401),
    INVITE_INVALID("INVITE_INVALID", "邀请码无效", 401),
    INVITE_EXPIRED("INVITE_EXPIRED", "邀请码已过期", 401),
    INVITE_ALREADY_USED("INVITE_ALREADY_USED", "邀请码已被使用", 401),
    INVITE_MISMATCH("INVITE_MISMATCH", "邀请信息与登录身份不匹配", 401),
    TOKEN_INVALID("TOKEN_INVALID", "Token无效", 401),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token已过期", 401),
    TOKEN_REVOKED("TOKEN_REVOKED", "Token已被撤销，请重新登录", 401),
    TOKEN_TYPE_ERROR("TOKEN_TYPE_ERROR", "Token类型错误", 401),
    REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", "refresh token 已过期", 401),
    REFRESH_TOKEN_FAILED("REFRESH_TOKEN_FAILED", "Token刷新失败: %s", 500),
    CSRF_INVALID("CSRF_INVALID", "CSRF 校验失败", 403),
    LOGIN_LOCKED("LOGIN_LOCKED", "登录失败次数过多，请稍后重试", 429),

    // ========== SSO ==========
    SSO_PROVIDER_NOT_FOUND("SSO_PROVIDER_NOT_FOUND", "SSO提供方不存在: %s", 400),
    SSO_CONFIG_NOT_FOUND("SSO_CONFIG_NOT_FOUND", "SSO配置不存在: provider=%s", 404),
    SSO_CONFIG_DISABLED("SSO_CONFIG_DISABLED", "SSO配置已禁用: provider=%s", 403),
    SSO_CONFIG_INVALID("SSO_CONFIG_INVALID", "SSO配置无效: %s", 500),
    SSO_STATE_INVALID("SSO_STATE_INVALID", "SSO state 无效或已过期", 401),
    SSO_STATE_GENERATE_FAILED("SSO_STATE_GENERATE_FAILED", "SSO state 生成失败", 500),
    SSO_STATE_MISMATCH("SSO_STATE_MISMATCH", "SSO state 与租户或提供方不匹配", 401),
    SSO_USER_ID_MISSING("SSO_USER_ID_MISSING", "SSO用户标识缺失", 500),
    SSO_REQUEST_FAILED("SSO_REQUEST_FAILED", "SSO请求失败: %s", 500),

    // ========== 业务 ==========
    ROLE_NOT_FOUND("ROLE_NOT_FOUND", "角色不存在: roleId=%s", 404),
    ROLE_ALREADY_EXISTS("ROLE_ALREADY_EXISTS", "角色代码已存在: %s", 409),
    ROLE_IN_USE("ROLE_IN_USE", "角色正在使用中，无法删除: roleId=%s", 409),
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "项目不存在: projectId=%s", 404),
    PROJECT_ACCESS_DENIED("PROJECT_ACCESS_DENIED", "无权限访问该项目: projectId=%s", 403),
    AIRFLOW_AUTH_FAILED("AIRFLOW_AUTH_FAILED", "Airflow 认证失败", 500),
    AIRFLOW_REQUEST_FAILED("AIRFLOW_REQUEST_FAILED", "Airflow 请求失败: %s", 500),
    NAMESPACE_NOT_FOUND("NAMESPACE_NOT_FOUND", "命名空间不存在: namespaceId=%s", 404),
    WORKFLOW_NOT_FOUND("WORKFLOW_NOT_FOUND", "工作流不存在: workflowId=%s", 404),
    WORKFLOW_DEPLOY_FAILED("WORKFLOW_DEPLOY_FAILED", "工作流部署失败: %s", 500),
    WORKFLOW_INVALID_STATUS("WORKFLOW_INVALID_STATUS", "工作流状态不正确: %s", 400),
    JOB_NOT_FOUND("JOB_NOT_FOUND", "任务不存在: jobId=%s", 404),
    JOB_TYPE_NOT_FOUND("JOB_TYPE_NOT_FOUND", "任务类型不存在: jobType=%s", 404),
    COMPONENT_NOT_FOUND("COMPONENT_NOT_FOUND", "组件不存在: code=%s", 404),
    DEPENDENCY_EXISTS("DEPENDENCY_EXISTS", "依赖关系已存在", 409),
    DEPENDENCY_NOT_FOUND("DEPENDENCY_NOT_FOUND", "依赖关系不存在", 404),
    DAG_HAS_CYCLE("DAG_HAS_CYCLE", "工作流存在循环依赖", 400);

    /**
     * 错误码
     */
    @Getter
    private final String code;

    /**
     * 错误消息模板
     */
    @Getter
    private final String messageTemplate;

    /**
     * HTTP 状态码
     */
    @Getter
    private final int httpStatus;

    ErrorCode(String code, String messageTemplate, int httpStatus) {
        this.code = code;
        this.messageTemplate = messageTemplate;
        this.httpStatus = httpStatus;
    }

    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }
}
