package com.sunny.datapillar.common.constant;

/**
 * 统一错误类型常量
 * 业务语义统一通过 type 字段传递
 *
 * @author Sunny
 * @date 2026-02-23
 */
public final class ErrorType {

    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ALREADY_EXISTS = "ALREADY_EXISTS";
    public static final String CONFLICT = "CONFLICT";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    public static final String BAD_GATEWAY = "BAD_GATEWAY";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String REQUIRED = "REQUIRED";

    public static final String TENANT_KEY_NOT_FOUND = "TENANT_KEY_NOT_FOUND";
    public static final String TENANT_KEY_INVALID = "TENANT_KEY_INVALID";
    public static final String PURPOSE_NOT_ALLOWED = "PURPOSE_NOT_ALLOWED";
    public static final String CIPHERTEXT_INVALID = "CIPHERTEXT_INVALID";
    public static final String KEY_STORAGE_UNAVAILABLE = "KEY_STORAGE_UNAVAILABLE";
    public static final String TENANT_PRIVATE_KEY_ALREADY_EXISTS = "TENANT_PRIVATE_KEY_ALREADY_EXISTS";
    public static final String TENANT_PUBLIC_KEY_MISSING = "TENANT_PUBLIC_KEY_MISSING";
    public static final String TENANT_PRIVATE_KEY_MISSING = "TENANT_PRIVATE_KEY_MISSING";

    private ErrorType() {
    }
}
