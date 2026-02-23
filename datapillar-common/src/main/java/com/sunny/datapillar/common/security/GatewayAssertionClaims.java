package com.sunny.datapillar.common.security;

/**
 * 网关断言Claims组件
 * 负责网关断言Claims核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class GatewayAssertionClaims {

    public static final String AUDIENCE = "aud";
    public static final String TENANT_ID = "tenantId";
    public static final String TENANT_CODE = "tenantCode";
    public static final String USERNAME = "username";
    public static final String EMAIL = "email";
    public static final String ROLES = "roles";
    public static final String IMPERSONATION = "impersonation";
    public static final String ACTOR_USER_ID = "actorUserId";
    public static final String ACTOR_TENANT_ID = "actorTenantId";
    public static final String METHOD = "method";
    public static final String PATH = "path";

    private GatewayAssertionClaims() {
    }
}
