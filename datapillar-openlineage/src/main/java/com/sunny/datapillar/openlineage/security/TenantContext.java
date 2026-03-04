package com.sunny.datapillar.openlineage.security;

import com.sunny.datapillar.openlineage.model.TenantSourceType;

/** event tenant context. */
public record TenantContext(
    Long tenantId, String tenantCode, String tenantName, TenantSourceType sourceType) {}
