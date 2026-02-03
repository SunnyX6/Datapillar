package com.sunny.datapillar.studio.context;

/**
 * 租户上下文
 */
public class TenantContext {
    private final Long tenantId;
    private final Long actorUserId;
    private final Long actorTenantId;
    private final boolean impersonation;

    public TenantContext(Long tenantId, Long actorUserId, Long actorTenantId, boolean impersonation) {
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.actorTenantId = actorTenantId;
        this.impersonation = impersonation;
    }

    public Long getTenantId() {
        return tenantId;
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
