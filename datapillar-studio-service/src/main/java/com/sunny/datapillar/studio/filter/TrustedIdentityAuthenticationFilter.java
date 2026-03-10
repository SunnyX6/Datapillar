package com.sunny.datapillar.studio.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import com.sunny.datapillar.studio.security.TrustedIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates requests from gateway-injected trusted identity headers. */
@Slf4j
@Component
public class TrustedIdentityAuthenticationFilter extends OncePerRequestFilter {

  private final TrustedIdentityProperties properties;
  private final SecurityExceptionHandler securityExceptionHandler;

  public TrustedIdentityAuthenticationFilter(
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

    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      chain.doFilter(request, response);
      return;
    }

    String issuer = trimToNull(request.getHeader(HeaderConstants.HEADER_PRINCIPAL_ISS));
    String subject = trimToNull(request.getHeader(HeaderConstants.HEADER_PRINCIPAL_SUB));
    PrincipalType principalType =
        PrincipalType.fromValue(request.getHeader(HeaderConstants.HEADER_PRINCIPAL_TYPE));
    String principalId = trimToNull(request.getHeader(HeaderConstants.HEADER_PRINCIPAL_ID));
    Long userId = parsePositiveLong(request.getHeader(HeaderConstants.HEADER_USER_ID));
    Long tenantId = parsePositiveLong(request.getHeader(HeaderConstants.HEADER_TENANT_ID));
    String tenantCode = trimToNull(request.getHeader(HeaderConstants.HEADER_TENANT_CODE));
    String username = trimToNull(request.getHeader(HeaderConstants.HEADER_USERNAME));
    String email = trimToNull(request.getHeader(HeaderConstants.HEADER_EMAIL));
    List<String> roleCodes = resolveRoleCodes(request);
    boolean impersonation = parseBoolean(request.getHeader(HeaderConstants.HEADER_IMPERSONATION));
    Long actorUserId = parsePositiveLong(request.getHeader(HeaderConstants.HEADER_ACTOR_USER_ID));
    Long actorTenantId =
        parseNonNegativeLong(request.getHeader(HeaderConstants.HEADER_ACTOR_TENANT_ID));

    if (!StringUtils.hasText(issuer) || !StringUtils.hasText(subject)) {
      reject(request, response, new UnauthorizedException("principal_header_missing"));
      return;
    }
    if (principalType == null || !StringUtils.hasText(principalId)) {
      reject(request, response, new UnauthorizedException("principal_type_missing"));
      return;
    }
    if (tenantId == null) {
      reject(request, response, new UnauthorizedException("trusted_tenant_context_missing"));
      return;
    }
    if (principalType.requiresUserId() && userId == null) {
      reject(request, response, new UnauthorizedException("trusted_user_context_missing"));
      return;
    }
    if (!principalType.requiresUserId() && userId != null) {
      reject(request, response, new UnauthorizedException("trusted_api_key_context_invalid"));
      return;
    }
    if (!StringUtils.hasText(tenantCode)) {
      reject(request, response, new UnauthorizedException("tenant_code_missing"));
      return;
    }
    if (roleCodes.isEmpty()) {
      reject(request, response, new UnauthorizedException("user_roles_missing"));
      return;
    }
    if (impersonation && (actorUserId == null || actorTenantId == null)) {
      reject(request, response, new UnauthorizedException("actor_context_missing"));
      return;
    }
    if (!impersonation && (actorUserId != null || actorTenantId != null)) {
      reject(request, response, new UnauthorizedException("actor_context_invalid"));
      return;
    }
    if (principalType != PrincipalType.USER && impersonation) {
      reject(request, response, new UnauthorizedException("actor_context_invalid"));
      return;
    }

    TrustedIdentityContext context =
        new TrustedIdentityContext(
            principalType,
            principalId,
            userId,
            tenantId,
            tenantCode,
            username,
            email,
            roleCodes,
            impersonation,
            actorUserId,
            actorTenantId,
            null);
    TrustedIdentityContext.attach(request, context);

    UsernamePasswordAuthenticationToken authToken =
        new UsernamePasswordAuthenticationToken(
            context.username() == null ? context.principalId() : context.username(),
            null,
            roleCodes.stream().map(SimpleGrantedAuthority::new).toList());
    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authToken);
    log.info(
        "security_event event=trusted_identity_resolved principal_type={} principal_id={} iss={} sub={} user_id={} tenant_id={} tenant_code={} impersonation={} trace_id={}",
        principalType,
        principalId,
        issuer,
        subject,
        context.userId(),
        context.tenantId(),
        tenantCode,
        context.impersonation(),
        trimToNull(request.getHeader(HeaderConstants.HEADER_TRACE_ID)));

    chain.doFilter(request, response);
  }

  private List<String> resolveRoleCodes(HttpServletRequest request) {
    String rawRoleHeader = trimToNull(request.getHeader(HeaderConstants.HEADER_USER_ROLES));
    LinkedHashSet<String> roleCodes = new LinkedHashSet<>();
    if (StringUtils.hasText(rawRoleHeader)) {
      for (String rawRole : rawRoleHeader.split(",")) {
        String normalizedRole = normalizeRoleCode(rawRole);
        if (normalizedRole != null) {
          roleCodes.add(normalizedRole);
        }
      }
    }
    return new ArrayList<>(roleCodes);
  }

  private String normalizeRoleCode(String rawRole) {
    if (!StringUtils.hasText(rawRole)) {
      return null;
    }
    return rawRole.trim().toUpperCase(Locale.ROOT);
  }

  private void reject(
      HttpServletRequest request, HttpServletResponse response, UnauthorizedException exception)
      throws IOException {
    log.warn(
        "security_event event=trusted_identity_authentication_failed path={} method={} reason={}",
        TrustedIdentityRequestSupport.normalizedPath(request),
        request.getMethod(),
        exception.getMessage());
    securityExceptionHandler.writeError(response, exception);
  }

  private Long parsePositiveLong(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      long parsed = Long.parseLong(value.trim());
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Long parseNonNegativeLong(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      long parsed = Long.parseLong(value.trim());
      return parsed >= 0 ? parsed : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private boolean parseBoolean(String value) {
    return Boolean.parseBoolean(trimToNull(value));
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
