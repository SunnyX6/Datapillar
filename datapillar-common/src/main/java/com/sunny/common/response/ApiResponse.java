package com.sunny.common.response;

import com.sunny.common.enums.GlobalSystemCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应格式
 * 用于所有服务的HTTP响应
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    /**
     * 响应码
     */
    private String code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 添加success字段用于兼容
     */
    public boolean isSuccess() {
        return GlobalSystemCode.SUCCESS.getCode().equals(code);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(GlobalSystemCode.SUCCESS.getCode(), GlobalSystemCode.SUCCESS.getMessageTemplate(), data);
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(GlobalSystemCode.SUCCESS.getCode(), GlobalSystemCode.SUCCESS.getMessageTemplate(), null);
    }

    /**
     * 成功响应（带消息）
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(GlobalSystemCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 错误响应（带消息）
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(GlobalSystemCode.ERROR.getCode(), message, null);
    }

    /**
     * 错误响应（带GlobalSystemCode）
     */
    public static <T> ApiResponse<T> error(GlobalSystemCode globalSystemCode, Object... args) {
        return new ApiResponse<>(globalSystemCode.getCode(), globalSystemCode.formatMessage(args), null);
    }

    /**
     * 错误响应（带代码和消息）
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 验证错误响应
     */
    public static <T> ApiResponse<T> validationError(String message) {
        return new ApiResponse<>(GlobalSystemCode.VALIDATION_ERROR.getCode(), message, null);
    }

    /**
     * 未授权响应
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(GlobalSystemCode.UNAUTHORIZED.getCode(), message, null);
    }

    /**
     * 禁止访问响应
     */
    public static <T> ApiResponse<T> forbidden(String message) {
        return new ApiResponse<>(GlobalSystemCode.FORBIDDEN.getCode(), message, null);
    }

    /**
     * 资源未找到响应
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return new ApiResponse<>(GlobalSystemCode.RESOURCE_NOT_FOUND.getCode(), message, null);
    }

    /**
     * 成功响应（带数据）- success是ok的别名
     */
    public static <T> ApiResponse<T> success(T data) {
        return ok(data);
    }

    /**
     * 成功响应（无数据）- success是ok的别名
     */
    public static <T> ApiResponse<T> success() {
        return ok();
    }

    /**
     * 成功响应（带消息）- success是ok的别名
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ok(message, data);
    }
}
