package com.sunny.datapillar.common.error;

import lombok.Getter;

/**
 * 统一错误码清单
 */
public enum ErrorCode {

    // ========== 成功码 ==========
    OK("OK", "操作成功", 200),

    // ========== Common ==========
    COMMON_INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "服务器内部错误", 500),

    // ========== Auth ==========
    AUTH_VALIDATION_ERROR("AUTH_VALIDATION_ERROR", "参数验证失败", 400),
    AUTH_INVALID_ARGUMENT("AUTH_INVALID_ARGUMENT", "参数错误", 400),
    AUTH_DUPLICATE_KEY("AUTH_DUPLICATE_KEY", "资源已存在", 409),
    AUTH_INTERNAL_ERROR("AUTH_INTERNAL_ERROR", "服务器内部错误", 500),
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未授权访问", 401),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "无权限访问", 403),
    AUTH_USER_NOT_FOUND("AUTH_USER_NOT_FOUND", "用户不存在: userId=%s", 401),
    AUTH_USER_DISABLED("AUTH_USER_DISABLED", "用户已被禁用", 403),
    AUTH_TENANT_NOT_FOUND("AUTH_TENANT_NOT_FOUND", "租户不存在: %s", 401),
    AUTH_TENANT_DISABLED("AUTH_TENANT_DISABLED", "租户已被禁用: tenantId=%s", 403),
    AUTH_TENANT_USER_DISABLED("AUTH_TENANT_USER_DISABLED", "租户成员已被禁用: tenantId=%s,userId=%s", 403),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "用户名或密码错误", 401),
    AUTH_INVITE_REQUIRED("AUTH_INVITE_REQUIRED", "首次入库必须使用邀请码", 401),
    AUTH_INVITE_INVALID("AUTH_INVITE_INVALID", "邀请码无效", 401),
    AUTH_INVITE_EXPIRED("AUTH_INVITE_EXPIRED", "邀请码已过期", 401),
    AUTH_INVITE_ALREADY_USED("AUTH_INVITE_ALREADY_USED", "邀请码已被使用", 401),
    AUTH_INVITE_MISMATCH("AUTH_INVITE_MISMATCH", "邀请信息与登录身份不匹配", 401),
    AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "Token无效", 401),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Token已过期", 401),
    AUTH_TOKEN_REVOKED("AUTH_TOKEN_REVOKED", "Token已被撤销，请重新登录", 401),
    AUTH_TOKEN_TYPE_ERROR("AUTH_TOKEN_TYPE_ERROR", "Token类型错误", 401),
    AUTH_REFRESH_TOKEN_EXPIRED("AUTH_REFRESH_TOKEN_EXPIRED", "refresh token 已过期", 401),
    AUTH_REFRESH_TOKEN_FAILED("AUTH_REFRESH_TOKEN_FAILED", "Token刷新失败: %s", 500),
    AUTH_SSO_PROVIDER_NOT_FOUND("AUTH_SSO_PROVIDER_NOT_FOUND", "SSO提供方不存在: %s", 400),
    AUTH_SSO_CONFIG_NOT_FOUND("AUTH_SSO_CONFIG_NOT_FOUND", "SSO配置不存在: provider=%s", 404),
    AUTH_SSO_CONFIG_DISABLED("AUTH_SSO_CONFIG_DISABLED", "SSO配置已禁用: provider=%s", 403),
    AUTH_SSO_CONFIG_INVALID("AUTH_SSO_CONFIG_INVALID", "SSO配置无效: %s", 500),
    AUTH_SSO_STATE_INVALID("AUTH_SSO_STATE_INVALID", "SSO state 无效或已过期", 401),
    AUTH_SSO_STATE_GENERATE_FAILED("AUTH_SSO_STATE_GENERATE_FAILED", "SSO state 生成失败", 500),
    AUTH_SSO_STATE_MISMATCH("AUTH_SSO_STATE_MISMATCH", "SSO state 与租户或提供方不匹配", 401),
    AUTH_SSO_USER_ID_MISSING("AUTH_SSO_USER_ID_MISSING", "SSO用户标识缺失", 500),
    AUTH_SSO_REQUEST_FAILED("AUTH_SSO_REQUEST_FAILED", "SSO请求失败: %s", 500),

    // ========== Admin ==========
    ADMIN_VALIDATION_ERROR("ADMIN_VALIDATION_ERROR", "参数验证失败", 400),
    ADMIN_INVALID_ARGUMENT("ADMIN_INVALID_ARGUMENT", "参数错误", 400),
    ADMIN_RESOURCE_NOT_FOUND("ADMIN_RESOURCE_NOT_FOUND", "资源不存在", 404),
    ADMIN_DUPLICATE_RESOURCE("ADMIN_DUPLICATE_RESOURCE", "资源已存在", 409),
    ADMIN_DUPLICATE_KEY("ADMIN_DUPLICATE_KEY", "资源已存在", 409),
    ADMIN_DUPLICATE_PARAM_KEY("ADMIN_DUPLICATE_PARAM_KEY", "参数键已存在", 409),
    ADMIN_FORBIDDEN("ADMIN_FORBIDDEN", "无权限访问", 403),
    ADMIN_INTERNAL_ERROR("ADMIN_INTERNAL_ERROR", "服务器内部错误", 500),
    ADMIN_UNAUTHORIZED("ADMIN_UNAUTHORIZED", "未授权访问", 401),
    ADMIN_USER_NOT_LOGGED_IN("ADMIN_USER_NOT_LOGGED_IN", "用户未登录", 401),
    ADMIN_USER_NOT_FOUND("ADMIN_USER_NOT_FOUND", "用户不存在: %s", 404),
    ADMIN_USER_ALREADY_EXISTS("ADMIN_USER_ALREADY_EXISTS", "用户名已存在: %s", 409),
    ADMIN_USERNAME_IN_USE("ADMIN_USERNAME_IN_USE", "用户名已被其他用户使用: %s", 409),
    ADMIN_PASSWORD_MISMATCH("ADMIN_PASSWORD_MISMATCH", "新密码和确认密码不一致", 400),
    ADMIN_CURRENT_PASSWORD_INCORRECT("ADMIN_CURRENT_PASSWORD_INCORRECT", "当前密码不正确", 400),
    ADMIN_ROLE_NOT_FOUND("ADMIN_ROLE_NOT_FOUND", "角色不存在: roleId=%s", 404),
    ADMIN_ROLE_ALREADY_EXISTS("ADMIN_ROLE_ALREADY_EXISTS", "角色代码已存在: %s", 409),
    ADMIN_ROLE_IN_USE("ADMIN_ROLE_IN_USE", "角色正在使用中，无法删除: roleId=%s", 409),
    ADMIN_PROJECT_NOT_FOUND("ADMIN_PROJECT_NOT_FOUND", "项目不存在: projectId=%s", 404),
    ADMIN_PROJECT_ACCESS_DENIED("ADMIN_PROJECT_ACCESS_DENIED", "无权限访问该项目: projectId=%s", 403),
    ADMIN_AIRFLOW_AUTH_FAILED("ADMIN_AIRFLOW_AUTH_FAILED", "Airflow 认证失败", 500),
    ADMIN_AIRFLOW_REQUEST_FAILED("ADMIN_AIRFLOW_REQUEST_FAILED", "Airflow 请求失败: %s", 500),
    ADMIN_NAMESPACE_NOT_FOUND("ADMIN_NAMESPACE_NOT_FOUND", "命名空间不存在: namespaceId=%s", 404),
    ADMIN_WORKFLOW_NOT_FOUND("ADMIN_WORKFLOW_NOT_FOUND", "工作流不存在: workflowId=%s", 404),
    ADMIN_WORKFLOW_DEPLOY_FAILED("ADMIN_WORKFLOW_DEPLOY_FAILED", "工作流部署失败: %s", 500),
    ADMIN_WORKFLOW_INVALID_STATUS("ADMIN_WORKFLOW_INVALID_STATUS", "工作流状态不正确: %s", 400),
    ADMIN_JOB_NOT_FOUND("ADMIN_JOB_NOT_FOUND", "任务不存在: jobId=%s", 404),
    ADMIN_JOB_TYPE_NOT_FOUND("ADMIN_JOB_TYPE_NOT_FOUND", "任务类型不存在: jobType=%s", 404),
    ADMIN_COMPONENT_NOT_FOUND("ADMIN_COMPONENT_NOT_FOUND", "组件不存在: code=%s", 404),
    ADMIN_DEPENDENCY_EXISTS("ADMIN_DEPENDENCY_EXISTS", "依赖关系已存在", 409),
    ADMIN_DEPENDENCY_NOT_FOUND("ADMIN_DEPENDENCY_NOT_FOUND", "依赖关系不存在", 404),
    ADMIN_DAG_HAS_CYCLE("ADMIN_DAG_HAS_CYCLE", "工作流存在循环依赖", 400);

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
