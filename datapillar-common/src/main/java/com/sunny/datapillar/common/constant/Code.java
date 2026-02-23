package com.sunny.datapillar.common.constant;

/**
 * 统一错误码常量
 * 全项目仅允许使用该集合中的状态码
 *
 * @author Sunny
 * @date 2026-02-23
 */
public final class Code {

    public static final int OK = 0;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int CONFLICT = 409;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int INTERNAL_ERROR = 500;
    public static final int BAD_GATEWAY = 502;
    public static final int SERVICE_UNAVAILABLE = 503;

    private Code() {
    }
}
