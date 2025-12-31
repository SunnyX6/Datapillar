package com.sunny.datapillar.admin.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Web Admin 模块错误码枚举
 */
@Getter
@AllArgsConstructor
public enum WebAdminErrorCode {

    // ========== 成功码 ==========
    SUCCESS("OK", "操作成功"),

    // ========== 通用错误 ==========
    ERROR("ERROR", "操作失败"),
    RESOURCE_NOT_FOUND("NOT_FOUND", "资源不存在"),
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", "资源已存在"),
    FORBIDDEN("FORBIDDEN", "无权限访问"),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数验证失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误"),

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

    // ========== 安全模块 ==========
    UNAUTHORIZED("UNAUTHORIZED", "未授权访问"),
    USER_NOT_LOGGED_IN("USER_NOT_LOGGED_IN", "用户未登录"),

    // ========== Airflow 模块 ==========
    AIRFLOW_AUTH_FAILED("AIRFLOW_AUTH_FAILED", "Airflow 认证失败"),
    AIRFLOW_REQUEST_FAILED("AIRFLOW_REQUEST_FAILED", "Airflow 请求失败: %s"),

    // ========== 工作流模块 ==========
    NAMESPACE_NOT_FOUND("NAMESPACE_NOT_FOUND", "命名空间不存在: namespaceId=%s"),
    WORKFLOW_NOT_FOUND("WORKFLOW_NOT_FOUND", "工作流不存在: workflowId=%s"),
    WORKFLOW_DEPLOY_FAILED("WORKFLOW_DEPLOY_FAILED", "工作流部署失败: %s"),
    WORKFLOW_INVALID_STATUS("WORKFLOW_INVALID_STATUS", "工作流状态不正确: %s"),
    JOB_NOT_FOUND("JOB_NOT_FOUND", "任务不存在: jobId=%s"),
    JOB_TYPE_NOT_FOUND("JOB_TYPE_NOT_FOUND", "任务类型不存在: jobType=%s"),
    COMPONENT_NOT_FOUND("COMPONENT_NOT_FOUND", "组件不存在: code=%s"),
    DEPENDENCY_EXISTS("DEPENDENCY_EXISTS", "依赖关系已存在"),
    DEPENDENCY_NOT_FOUND("DEPENDENCY_NOT_FOUND", "依赖关系不存在"),
    DAG_HAS_CYCLE("DAG_HAS_CYCLE", "工作流存在循环依赖");

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
