package com.sunny.datapillar.openlineage.web.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.handler.SecurityExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Gateway trusted identity authentication filter. */
@Slf4j
@Component
public class TrustedIdentityAuthenticationFilter
    extends org.springframework.web.filter.OncePerRequestFilter {

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
    String tenantCode = trimToNull(request.getHeader(HeaderConstants.HEADER_TENANT_CODE));
    String username = trimToNull(request.getHeader(HeaderConstants.HEADER_USERNAME));
    String email = trimToNull(request.getHeader(HeaderConstants.HEADER_EMAIL));
    Long userId = parsePositiveLong(request.getHeader(HeaderConstants.HEADER_USER_ID));
    Long tenantId = parsePositiveLong(request.getHeader(HeaderConstants.HEADER_TENANT_ID));
    List<String> roles = parseRoles(request.getHeader(HeaderConstants.HEADER_USER_ROLES));
    boolean impersonation = parseBoolean(request.getHeader(HeaderConstants.HEADER_IMPERSONATION));
    Long actorUserId = parsePositiveLong(request.getHeader(HeaderConstants.HEADER_ACTOR_USER_ID));
    Long actorTenantId =
        parseNonNegativeLong(request.getHeader(HeaderConstants.HEADER_ACTOR_TENANT_ID));
    String traceId = trimToNull(request.getHeader(HeaderConstants.HEADER_TRACE_ID));

    if (issuer == null || subject == null) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("principal_header_missing"));
      return;
    }
    if (principalType == null || !StringUtils.hasText(principalId)) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("principal_type_missing"));
      return;
    }
    if (tenantId == null) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("trusted_tenant_context_missing"));
      return;
    }
    if (principalType.requiresUserId() && userId == null) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("trusted_user_context_missing"));
      return;
    }
    if (!principalType.requiresUserId() && userId != null) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("trusted_api_key_context_invalid"));
      return;
    }
    if (!StringUtils.hasText(tenantCode)) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("tenant_code_missing"));
      return;
    }
    if (roles.isEmpty()) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("user_roles_missing"));
      return;
    }
    if (impersonation && (actorUserId == null || actorTenantId == null)) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("actor_context_missing"));
      return;
    }
    if (!impersonation && (actorUserId != null || actorTenantId != null)) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("actor_context_invalid"));
      return;
    }
    if (principalType != PrincipalType.USER && impersonation) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("actor_context_invalid"));
      return;
    }

    String method = request.getMethod();
    String path = TrustedIdentityRequestSupport.normalizedPath(request);
    try {
      TrustedIdentityContext context =
          new TrustedIdentityContext(
              principalType,
              principalId,
              userId,
              tenantId,
              tenantCode,
              username == null ? subject : username,
              email,
              roles,
              impersonation,
              actorUserId,
              actorTenantId,
              issuer,
              subject,
              traceId);
      TrustedIdentityContext.attach(request, context);
      TrustedIdentityContextHolder.set(context);

      List<SimpleGrantedAuthority> authorities =
          roles.stream()
              .map(role -> role == null ? "" : role.trim())
              .filter(role -> !role.isEmpty())
              .map(role -> new SimpleGrantedAuthority(role.toUpperCase()))
              .toList();
      if (authorities.isEmpty()) {
        authorities = List.of(new SimpleGrantedAuthority("USER"));
      }

      UsernamePasswordAuthenticationToken authToken =
          new UsernamePasswordAuthenticationToken(
              context.username() == null ? context.principalId() : context.username(),
              null,
              authorities);
      authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authToken);

      log.info(
          "security_event event=trusted_identity_resolved principal_type={} principal_id={} iss={} sub={} user_id={} tenant_id={} tenant_code={} trace_id={}",
          context.principalType(),
          context.principalId(),
          context.issuer(),
          context.subject(),
          context.userId(),
          context.tenantId(),
          context.tenantCode(),
          context.traceId() == null ? "" : context.traceId());

      chain.doFilter(request, response);
    } catch (Throwable ex) {
      log.warn(
          "security_event event=trusted_identity_resolve_failed path={} method={} reason={}",
          path,
          method,
          ex.getMessage());
      securityExceptionHandler.writeError(
          response, new UnauthorizedException(ex, "Unauthorized access"));
    }
  }

  private Long parsePositiveLong(String raw) {
    String value = trimToNull(raw);
    if (value == null) {
      return null;
    }
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) {
        return null;
      }
      return parsed;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Long parseNonNegativeLong(String raw) {
    String value = trimToNull(raw);
    if (value == null) {
      return null;
    }
    try {
      long parsed = Long.parseLong(value);
      return parsed >= 0 ? parsed : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private boolean parseBoolean(String raw) {
    return Boolean.parseBoolean(trimToNull(raw));
  }

  private List<String> parseRoles(String raw) {
    String value = trimToNull(raw);
    if (value == null) {
      return List.of();
    }
    return java.util.Arrays.stream(value.split(","))
        .map(this::trimToNull)
        .filter(token -> token != null && !token.isBlank())
        .map(String::toUpperCase)
        .toList();
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
