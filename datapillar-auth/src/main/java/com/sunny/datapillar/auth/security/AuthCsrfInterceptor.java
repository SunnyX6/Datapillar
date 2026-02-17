package com.sunny.datapillar.auth.security;

import java.util.Arrays;
import java.util.List;

import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.utils.JwtUtil;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.sunny.datapillar.common.exception.ForbiddenException;
/**
 * 认证CSRF拦截器
 * 负责认证CSRF请求拦截与访问校验
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Component
public class AuthCsrfInterceptor implements HandlerInterceptor {

    private static final List<String> CSRF_WHITELIST = Arrays.asList(
            "/login",
            "/login/sso",
            "/login/logout",
            "/auth/health",
            "/auth/validate"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AuthSecurityProperties securityProperties;
    private final CsrfTokenStore csrfTokenStore;
    private final JwtUtil jwtUtil;

    public AuthCsrfInterceptor(AuthSecurityProperties securityProperties,
                               CsrfTokenStore csrfTokenStore,
                               JwtUtil jwtUtil) {
        this.securityProperties = securityProperties;
        this.csrfTokenStore = csrfTokenStore;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
        if ((authCookie == null || authCookie.isBlank()) && (refreshCookie == null || refreshCookie.isBlank())) {
            return true;
        }

        boolean refreshRequest = pathMatcher.match("/auth/refresh", path);
        String headerName = refreshRequest
                ? securityProperties.getCsrf().getRefreshHeaderName()
                : securityProperties.getCsrf().getHeaderName();
        String cookieName = refreshRequest
                ? securityProperties.getCsrf().getRefreshCookieName()
                : securityProperties.getCsrf().getCookieName();
        String headerToken = request.getHeader(headerName);
        String cookieToken = getCookieValue(request, cookieName);
        if (headerToken == null || cookieToken == null || !headerToken.equals(cookieToken)) {
            throw new ForbiddenException("CSRF 校验失败");
        }

        TokenIdentity tokenIdentity = resolveTokenIdentity(path, authCookie, refreshCookie);
        if (tokenIdentity == null) {
            throw new ForbiddenException(new IllegalArgumentException("token_identity_missing"), "CSRF 校验失败");
        }
        boolean valid = refreshRequest
                ? csrfTokenStore.validateRefreshToken(tokenIdentity.tenantId(), tokenIdentity.userId(), headerToken)
                : csrfTokenStore.validateToken(tokenIdentity.tenantId(), tokenIdentity.userId(), headerToken);
        if (!valid) {
            throw new ForbiddenException("CSRF 校验失败");
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
     * refresh 请求优先使用 refresh-token，避免 access-token 到期后 CSRF 身份解析失败。
     */
    private TokenIdentity resolveTokenIdentity(String path, String authCookie, String refreshCookie) {
        DatapillarRuntimeException parseFailure = null;
        if (pathMatcher.match("/auth/refresh", path)) {
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
            Claims claims = jwtUtil.parseToken(token);
            Long tenantId = jwtUtil.getTenantId(claims);
            Long userId = jwtUtil.getUserId(claims);
            if (tenantId == null || userId == null) {
                return null;
            }
            return new TokenIdentity(tenantId, userId);
        } catch (DatapillarRuntimeException e) {
            throw new ForbiddenException(e, "CSRF 校验失败", tokenName + "_parse_failed:" + e.getMessage());
        }
    }

    private record TokenIdentity(Long tenantId, Long userId) {
    }

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
