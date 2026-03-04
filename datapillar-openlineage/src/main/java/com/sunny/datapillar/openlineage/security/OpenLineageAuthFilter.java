package com.sunny.datapillar.openlineage.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.openlineage.handler.SecurityExceptionHandler;
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
import org.springframework.web.filter.OncePerRequestFilter;

/** Gateway Assertion Filter. */
@Slf4j
@Component
public class OpenLineageAuthFilter extends OncePerRequestFilter {

  private static final Set<String> WHITELIST_PREFIX =
      Set.of("/actuator/health", "/actuator/info", "/v3/api-docs");
  private final TrustedIdentityProperties properties;
  private final SecurityExceptionHandler securityExceptionHandler;

  public OpenLineageAuthFilter(
      TrustedIdentityProperties properties, SecurityExceptionHandler securityExceptionHandler) {
    this.properties = properties;
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
    Long userId = parsePositiveLong(request.getHeader(HeaderConstants.HEADER_USER_ID));
    Long tenantId = parsePositiveLong(request.getHeader(HeaderConstants.HEADER_TENANT_ID));
    List<String> roles = parseRoles(request.getHeader(HeaderConstants.HEADER_USER_ROLES));
    String traceId = trimToNull(request.getHeader(HeaderConstants.HEADER_TRACE_ID));
    if (issuer == null || subject == null) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("principal_header_missing"));
      return;
    }
    if (tenantCode == null) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("tenant_code_missing"));
      return;
    }
    if (tenantId == null || userId == null) {
      securityExceptionHandler.writeError(
          response, new UnauthorizedException("trusted_user_context_missing"));
      return;
    }

    String method = request.getMethod();
    String path = normalizedPath(request);
    try {
      TrustedIdentityContext context =
          new TrustedIdentityContext(
              userId,
              tenantId,
              tenantCode,
              username == null ? subject : username,
              email,
              roles,
              issuer,
              subject,
              traceId);
      TrustedIdentityContext.attach(request, context);
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
              context.username() == null ? context.subject() : context.username(),
              null,
              authorities);
      authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authToken);
      log.info(
          "security_event event=trusted_identity_resolved iss={} sub={} preferred_username={} tenant_code={} trace_id={}",
          context.issuer(),
          context.subject(),
          context.username(),
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
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
