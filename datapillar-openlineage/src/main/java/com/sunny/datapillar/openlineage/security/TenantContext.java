package com.sunny.datapillar.openlineage.security;

import com.sunny.datapillar.openlineage.model.TenantSourceType;

/**
 * 事件租户上下文。
 */
public record TenantContext(
        Long tenantId,
        String tenantCode,
        String tenantName,
        TenantSourceType sourceType
) {
}
