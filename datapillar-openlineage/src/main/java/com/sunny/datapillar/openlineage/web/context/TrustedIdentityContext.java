package com.sunny.datapillar.openlineage.web.context;

import com.sunny.datapillar.common.security.PrincipalType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/** Trusted identity context from gateway headers. */
public record TrustedIdentityContext(
    PrincipalType principalType,
    String principalId,
    Long userId,
    Long tenantId,
    String tenantCode,
    String username,
    String email,
    List<String> roles,
    boolean impersonation,
    Long actorUserId,
    Long actorTenantId,
    String issuer,
    String subject,
    String traceId) {

  public static final String REQUEST_ATTRIBUTE = TrustedIdentityContext.class.getName();

  public TrustedIdentityContext {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }

  public static void attach(HttpServletRequest request, TrustedIdentityContext context) {
    if (request == null || context == null) {
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
