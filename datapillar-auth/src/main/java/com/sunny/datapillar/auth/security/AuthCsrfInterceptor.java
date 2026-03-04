package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * CSRF interceptor for auth endpoints.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class AuthCsrfInterceptor implements HandlerInterceptor {

  private static final List<String> CSRF_WHITELIST =
      Arrays.asList(
          "/auth/health",
          "/auth/session/login",
          "/auth/session/oauth2/login",
          "/auth/session/oauth2/authorize",
          "/auth/session/logout",
          "/oauth2/token");

  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private final AuthSecurityProperties securityProperties;
  private final CsrfTokenStore csrfTokenStore;
  private final JwtToken jwtToken;

  public AuthCsrfInterceptor(
      AuthSecurityProperties securityProperties, CsrfTokenStore csrfTokenStore, JwtToken jwtToken) {
    this.securityProperties = securityProperties;
    this.csrfTokenStore = csrfTokenStore;
    this.jwtToken = jwtToken;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!securityProperties.getCsrf().isEnabled()) {
      return true;
    }
    if (isSafeMethod(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    if (isWhitelisted(path)) {
      return true;
    }

    if (hasAuthorizationHeader(request)) {
      return true;
    }

    String authCookie = getCookieValue(request, "auth-token");
    String refreshCookie = getCookieValue(request, "refresh-token");
    if ((authCookie == null || authCookie.isBlank())
        && (refreshCookie == null || refreshCookie.isBlank())) {
      return true;
    }

    boolean refreshRequest = pathMatcher.match("/auth/session/refresh", path);
    String headerName =
        refreshRequest
            ? securityProperties.getCsrf().getRefreshHeaderName()
            : securityProperties.getCsrf().getHeaderName();
    String cookieName =
        refreshRequest
            ? securityProperties.getCsrf().getRefreshCookieName()
            : securityProperties.getCsrf().getCookieName();
    String headerToken = request.getHeader(headerName);
    String cookieToken = getCookieValue(request, cookieName);
    if (headerToken == null || cookieToken == null || !headerToken.equals(cookieToken)) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("CSRF validation failed");
    }

    TokenIdentity tokenIdentity = resolveTokenIdentity(path, authCookie, refreshCookie);
    if (tokenIdentity == null) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          new IllegalArgumentException("token_identity_missing"), "CSRF validation failed");
    }
    boolean valid =
        refreshRequest
            ? csrfTokenStore.validateRefreshToken(
                tokenIdentity.tenantId(), tokenIdentity.userId(), headerToken)
            : csrfTokenStore.validateToken(
                tokenIdentity.tenantId(), tokenIdentity.userId(), headerToken);
    if (!valid) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException("CSRF validation failed");
    }

    return true;
  }

  private boolean isSafeMethod(String method) {
    return "GET".equalsIgnoreCase(method)
        || "HEAD".equalsIgnoreCase(method)
        || "OPTIONS".equalsIgnoreCase(method);
  }

  private boolean isWhitelisted(String path) {
    return CSRF_WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  /**
   * Prefer refresh-token for refresh requests to avoid CSRF identity parse failure after
   * access-token expiry.
   */
  private TokenIdentity resolveTokenIdentity(String path, String authCookie, String refreshCookie) {
    DatapillarRuntimeException parseFailure = null;
    if (pathMatcher.match("/auth/session/refresh", path)) {
      TokenIdentity refreshIdentity;
      try {
        refreshIdentity = parseIdentity(refreshCookie, "refresh-token");
      } catch (DatapillarRuntimeException ex) {
        parseFailure = ex;
        refreshIdentity = null;
      }
      if (refreshIdentity != null) {
        return refreshIdentity;
      }
      try {
        TokenIdentity authIdentity = parseIdentity(authCookie, "auth-token");
        if (authIdentity != null) {
          return authIdentity;
        }
      } catch (DatapillarRuntimeException ex) {
        if (parseFailure == null) {
          parseFailure = ex;
        }
      }
      if (parseFailure != null) {
        throw parseFailure;
      }
      return null;
    }

    TokenIdentity authIdentity;
    try {
      authIdentity = parseIdentity(authCookie, "auth-token");
    } catch (DatapillarRuntimeException ex) {
      parseFailure = ex;
      authIdentity = null;
    }
    if (authIdentity != null) {
      return authIdentity;
    }
    try {
      TokenIdentity refreshIdentity = parseIdentity(refreshCookie, "refresh-token");
      if (refreshIdentity != null) {
        return refreshIdentity;
      }
    } catch (DatapillarRuntimeException ex) {
      if (parseFailure == null) {
        parseFailure = ex;
      }
    }
    if (parseFailure != null) {
      throw parseFailure;
    }
    return null;
  }

  private TokenIdentity parseIdentity(String token, String tokenName) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      Claims claims = jwtToken.parseToken(token);
      Long tenantId = jwtToken.getTenantId(claims);
      Long userId = jwtToken.getUserId(claims);
      if (tenantId == null || userId == null) {
        return null;
      }
      return new TokenIdentity(tenantId, userId);
    } catch (DatapillarRuntimeException e) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          e, "CSRF validation failed", tokenName + "_parse_failed:" + e.getMessage());
    }
  }

  private record TokenIdentity(Long tenantId, Long userId) {}

  private boolean hasAuthorizationHeader(HttpServletRequest request) {
    String auth = request.getHeader("Authorization");
    return auth != null && auth.startsWith("Bearer ");
  }

  private String getCookieValue(HttpServletRequest request, String name) {
    if (request == null || name == null) {
      return null;
    }
    Cookie[] cookies = request.getCookies();
    if (cookies == null || cookies.length == 0) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
