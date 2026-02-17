package com.sunny.datapillar.common.constant;

/**
 * 请求头常量
 * 集中维护请求头常量定义
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class HeaderConstants {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_EMAIL = "X-User-Email";
    public static final String HEADER_ACTOR_USER_ID = "X-Actor-User-Id";
    public static final String HEADER_ACTOR_TENANT_ID = "X-Actor-Tenant-Id";
    public static final String HEADER_IMPERSONATION = "X-Impersonation";
    public static final String HEADER_GATEWAY_ASSERTION = "X-Gateway-Assertion";

    private HeaderConstants() {
    }
}
