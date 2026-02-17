package com.sunny.datapillar.common.constant;

/**
 * 错误常量
 * 集中维护错误常量定义
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class ErrorConstants {

    public static final int REST_ERROR_CODE = 1000;
    public static final int ILLEGAL_ARGUMENTS_CODE = 1001;
    public static final int INTERNAL_ERROR_CODE = 1002;
    public static final int NOT_FOUND_CODE = 1003;
    public static final int ALREADY_EXISTS_CODE = 1004;
    public static final int CONFLICT_CODE = 1005;
    public static final int UNSUPPORTED_OPERATION_CODE = 1006;
    public static final int CONNECTION_FAILED_CODE = 1007;
    public static final int FORBIDDEN_CODE = 1008;
    public static final int UNAUTHORIZED_CODE = 1009;
    public static final int TOO_MANY_REQUESTS_CODE = 1010;
    public static final int SERVICE_UNAVAILABLE_CODE = 1011;
    public static final int UNKNOWN_ERROR_CODE = 1100;

    private ErrorConstants() {
    }
}
