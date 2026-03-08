package com.sunny.datapillar.studio.filter;

import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import com.sunny.datapillar.studio.security.TrustedIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tenant context filter Responsible for tenant context request filtering and context control
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

  private static final String TENANT_ID_KEY = "tenantId";
  private static final String TENANT_CODE_KEY = "tenantCode";
  private static final String ACTOR_USER_ID_KEY = "actorUserId";
  private static final String ACTOR_TENANT_ID_KEY = "actorTenantId";
  private static final String IMPERSONATION_KEY = "impersonation";

  private final TrustedIdentityProperties properties;
  private final SecurityExceptionHandler securityExceptionHandler;

  public TenantContextFilter(
      TrustedIdentityProperties properties, SecurityExceptionHandler securityExceptionHandler) {
    this.properties = properties;
    this.securityExceptionHandler = securityExceptionHandler;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return TrustedIdentityRequestSupport.shouldSkip(request, properties.isEnabled());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    TrustedIdentityContext assertionContext = TrustedIdentityContext.current(request);
    if (assertionContext == null) {
      securityExceptionHandler.writeError(
          response,
          new com.sunny.datapillar.common.exception.UnauthorizedException(
              "trusted_identity_context_missing"));
      return;
    }

    Long tenantId = assertionContext.tenantId();
    if (tenantId == null) {
      securityExceptionHandler.writeError(
          response,
          new com.sunny.datapillar.common.exception.UnauthorizedException(
              "trusted_identity_tenant_id_missing"));
      return;
    }
    String tenantCode = assertionContext.tenantCode();
    if (!StringUtils.hasText(tenantCode)) {
      securityExceptionHandler.writeError(
          response,
          new com.sunny.datapillar.common.exception.UnauthorizedException(
              "trusted_identity_tenant_code_missing"));
      return;
    }

    boolean impersonation = assertionContext.impersonation();
    Long actorUserId = assertionContext.actorUserId();
    Long actorTenantId = assertionContext.actorTenantId();

    TenantContextHolder.set(
        new TenantContext(tenantId, tenantCode, actorUserId, actorTenantId, impersonation));
    MDC.put(TENANT_ID_KEY, String.valueOf(tenantId));
    MDC.put(TENANT_CODE_KEY, tenantCode);
    if (actorUserId != null) {
      MDC.put(ACTOR_USER_ID_KEY, String.valueOf(actorUserId));
    }
    if (actorTenantId != null) {
      MDC.put(ACTOR_TENANT_ID_KEY, String.valueOf(actorTenantId));
    }
    MDC.put(IMPERSONATION_KEY, String.valueOf(impersonation));

    try {
      chain.doFilter(request, response);
    } finally {
      TenantContextHolder.clear();
      MDC.remove(TENANT_ID_KEY);
      MDC.remove(TENANT_CODE_KEY);
      MDC.remove(ACTOR_USER_ID_KEY);
      MDC.remove(ACTOR_TENANT_ID_KEY);
      MDC.remove(IMPERSONATION_KEY);
    }
  }
}
