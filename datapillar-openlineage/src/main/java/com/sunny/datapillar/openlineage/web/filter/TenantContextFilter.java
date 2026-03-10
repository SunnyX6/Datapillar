package com.sunny.datapillar.openlineage.web.filter;

import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.openlineage.web.context.TenantContext;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.handler.SecurityExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Tenant context filter for API request isolation. */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

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
    TrustedIdentityContext identity = TrustedIdentityContextHolder.get();
    if (identity == null) {
      identity = TrustedIdentityContext.current(request);
    }
    if (identity == null) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("trusted_identity_context_missing"));
      return;
    }

    Long tenantId = identity.tenantId();
    if (tenantId == null || tenantId <= 0) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("trusted_identity_tenant_id_missing"));
      return;
    }

    String tenantCode = trimToNull(identity.tenantCode());
    if (!StringUtils.hasText(tenantCode)) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("trusted_identity_tenant_code_missing"));
      return;
    }

    TenantContextHolder.set(new TenantContext(tenantId, tenantCode));
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContextHolder.clear();
      TrustedIdentityContextHolder.clear();
    }
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
