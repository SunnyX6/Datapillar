package com.sunny.datapillar.studio.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

/**
 * Trusted identity context. Maintain gateway-injected trusted principal context state and access
 * capabilities.
 *
 * @author Sunny
 * @date 2026-01-01
 */
public record TrustedIdentityContext(
    Long userId,
    Long tenantId,
    String tenantCode,
    String username,
    String email,
    List<String> roles,
    boolean impersonation,
    Long actorUserId,
    Long actorTenantId,
    String tokenId) {

  public static final String REQUEST_ATTRIBUTE = TrustedIdentityContext.class.getName();

  public TrustedIdentityContext {
    roles = roles == null ? Collections.emptyList() : roles;
  }

  public static void attach(HttpServletRequest request, TrustedIdentityContext context) {
    if (request == null) {
      return;
    }
    request.setAttribute(REQUEST_ATTRIBUTE, context);
  }

  public static TrustedIdentityContext current(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object value = request.getAttribute(REQUEST_ATTRIBUTE);
    if (value instanceof TrustedIdentityContext context) {
      return context;
    }
    return null;
  }
}
