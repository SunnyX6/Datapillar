package com.sunny.datapillar.studio.context;

/**
 * 租户上下文
 * 维护租户上下文状态与访问能力
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class TenantContext {
    private final Long tenantId;
    private final String tenantCode;
    private final Long actorUserId;
    private final Long actorTenantId;
    private final boolean impersonation;

    public TenantContext(Long tenantId, String tenantCode, Long actorUserId, Long actorTenantId, boolean impersonation) {
        this.tenantId = tenantId;
        this.tenantCode = tenantCode;
        this.actorUserId = actorUserId;
        this.actorTenantId = actorTenantId;
        this.impersonation = impersonation;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Long getActorTenantId() {
        return actorTenantId;
    }

    public boolean isImpersonation() {
        return impersonation;
    }
}
