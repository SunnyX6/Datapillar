package com.sunny.datapillar.connector.spi;

/** Trusted identity context for connector calls. */
public record ConnectorContext(
    Long tenantId,
    String tenantCode,
    Long userId,
    String username,
    String principalSub,
    Long actorUserId,
    Long actorTenantId,
    boolean impersonation,
    String traceId,
    String requestId) {}
