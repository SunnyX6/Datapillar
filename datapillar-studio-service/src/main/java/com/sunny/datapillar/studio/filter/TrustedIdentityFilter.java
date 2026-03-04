package com.sunny.datapillar.studio.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.entity.UserIdentity;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserIdentityMapper;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import com.sunny.datapillar.studio.security.TrustedIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gateway trusted-context filter Consumes gateway-injected identity headers and resolves local user
 * mapping.
 */
@Slf4j
@Component
public class TrustedIdentityFilter extends OncePerRequestFilter {

  private static final Set<String> WHITELIST_PREFIX =
      Set.of("/actuator/health", "/actuator/info", "/v3/api-docs", "/setup", "/biz/invitations");

  private final TrustedIdentityProperties properties;
  private final UserIdentityMapper userIdentityMapper;
  private final TenantMapper tenantMapper;
  private final SecurityExceptionHandler securityExceptionHandler;

  public TrustedIdentityFilter(
      TrustedIdentityProperties properties,
      UserIdentityMapper userIdentityMapper,
      TenantMapper tenantMapper,
      SecurityExceptionHandler securityExceptionHandler) {
    this.properties = properties;
    this.userIdentityMapper = userIdentityMapper;
    this.tenantMapper = tenantMapper;
    this.securityExceptionHandler = securityExceptionHandler;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.isEnabled()) {
      return true;
    }
    String path = normalizedPathForWhitelist(request);
    for (String prefix : WHITELIST_PREFIX) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      chain.doFilter(request, response);
      return;
    }

    String issuer = trimToNull(request.getHeader(HeaderConstants.HEADER_PRINCIPAL_ISS));
    String subject = trimToNull(request.getHeader(HeaderConstants.HEADER_PRINCIPAL_SUB));
    String tenantCode = trimToNull(request.getHeader(HeaderConstants.HEADER_TENANT_CODE));
    String username = trimToNull(request.getHeader(HeaderConstants.HEADER_USERNAME));
    String email = trimToNull(request.getHeader(HeaderConstants.HEADER_EMAIL));

    if (!StringUtils.hasText(issuer) || !StringUtils.hasText(subject)) {
      securityExceptionHandler.writeError(
          response,
          new com.sunny.datapillar.common.exception.UnauthorizedException(
              "principal_header_missing"));
      return;
    }
    if (!StringUtils.hasText(tenantCode)) {
      securityExceptionHandler.writeError(
          response,
          new com.sunny.datapillar.common.exception.UnauthorizedException("tenant_code_missing"));
      return;
    }

    try {
      UserIdentity identity =
          userIdentityMapper.selectOne(
              new LambdaQueryWrapper<UserIdentity>()
                  .eq(UserIdentity::getIssuer, issuer)
                  .eq(UserIdentity::getSubject, subject)
                  .last("LIMIT 1"));
      if (identity == null || identity.getUserId() == null || identity.getUserId() <= 0) {
        securityExceptionHandler.writeError(
            response,
            new com.sunny.datapillar.common.exception.UnauthorizedException(
                "identity_mapping_not_found"));
        return;
      }

      Tenant tenant = tenantMapper.selectByCode(tenantCode);
      if (tenant == null || tenant.getId() == null) {
        securityExceptionHandler.writeError(
            response,
            new com.sunny.datapillar.common.exception.UnauthorizedException("tenant_not_found"));
        return;
      }
      if (tenant.getStatus() == null || tenant.getStatus() != 1) {
        securityExceptionHandler.writeError(
            response,
            new com.sunny.datapillar.common.exception.ForbiddenException("tenant_disabled"));
        return;
      }

      TrustedIdentityContext context =
          new TrustedIdentityContext(
              identity.getUserId(),
              tenant.getId(),
              tenantCode,
              StringUtils.hasText(username) ? username : subject,
              email,
              List.of("USER"),
              false,
              null,
              null,
              request.getHeader(HeaderConstants.HEADER_REQUEST_ID));
      TrustedIdentityContext.attach(request, context);

      UsernamePasswordAuthenticationToken authToken =
          new UsernamePasswordAuthenticationToken(
              context.username(), null, List.of(new SimpleGrantedAuthority("USER")));
      authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authToken);
      log.info(
          "security_event event=trusted_identity_resolved iss={} sub={} preferred_username={} tenant_code={} trace_id={}",
          issuer,
          subject,
          context.username(),
          tenantCode,
          trimToNull(request.getHeader(HeaderConstants.HEADER_TRACE_ID)));

      chain.doFilter(request, response);
    } catch (Throwable ex) {
      log.warn(
          "security_event event=trusted_header_resolve_failed path={} method={} reason={}",
          normalizedPath(request),
          request.getMethod(),
          ex.getMessage());
      securityExceptionHandler.writeError(
          response,
          new com.sunny.datapillar.common.exception.UnauthorizedException(
              ex, "Unauthorized access"));
    }
  }

  private String normalizedPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null || path.isBlank()) {
      return "/";
    }
    return path;
  }

  private String normalizedPathForWhitelist(HttpServletRequest request) {
    String path = normalizedPath(request);
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
      if (path.isEmpty()) {
        return "/";
      }
    }
    return path;
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
