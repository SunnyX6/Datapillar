package com.sunny.datapillar.openlineage.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

/**
 * 网关断言上下文。
 */
public record GatewayAssertionContext(
        Long userId,
        Long tenantId,
        String tenantCode,
        String tenantName,
        String username,
        String email,
        List<String> roles,
        boolean impersonation,
        Long actorUserId,
        Long actorTenantId,
        String tokenId) {

    public static final String REQUEST_ATTRIBUTE = GatewayAssertionContext.class.getName();

    public GatewayAssertionContext {
        roles = roles == null ? Collections.emptyList() : roles;
    }

    public static void attach(HttpServletRequest request, GatewayAssertionContext context) {
        if (request == null) {
            return;
        }
        request.setAttribute(REQUEST_ATTRIBUTE, context);
    }

    public static GatewayAssertionContext current(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof GatewayAssertionContext context) {
            return context;
        }
        return null;
    }
}
