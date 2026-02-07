package com.sunny.datapillar.auth.security;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthCsrfInterceptor implements HandlerInterceptor {

    private static final List<String> CSRF_WHITELIST = Arrays.asList(
            "/auth/login",
            "/auth/login/tenant",
            "/auth/sso/login",
            "/auth/sso/qr",
            "/auth/health",
            "/auth/validate"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AuthSecurityProperties securityProperties;
    private final CsrfTokenService csrfTokenService;
    private final JwtTokenUtil jwtTokenUtil;

    public AuthCsrfInterceptor(AuthSecurityProperties securityProperties,
                               CsrfTokenService csrfTokenService,
                               JwtTokenUtil jwtTokenUtil) {
        this.securityProperties = securityProperties;
        this.csrfTokenService = csrfTokenService;
        this.jwtTokenUtil = jwtTokenUtil;
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

        if (!isOriginAllowed(request)) {
            throw new BusinessException(ErrorCode.CSRF_INVALID);
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
            throw new BusinessException(ErrorCode.CSRF_INVALID);
        }

        TokenIdentity tokenIdentity = resolveTokenIdentity(path, authCookie, refreshCookie);
        if (tokenIdentity == null) {
            throw new BusinessException(ErrorCode.CSRF_INVALID);
        }
        boolean valid = refreshRequest
                ? csrfTokenService.validateRefreshToken(tokenIdentity.tenantId(), tokenIdentity.userId(), headerToken)
                : csrfTokenService.validateToken(tokenIdentity.tenantId(), tokenIdentity.userId(), headerToken);
        if (!valid) {
            throw new BusinessException(ErrorCode.CSRF_INVALID);
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
        if (pathMatcher.match("/auth/refresh", path)) {
            TokenIdentity refreshIdentity = parseIdentity(refreshCookie);
            if (refreshIdentity != null) {
                return refreshIdentity;
            }
            return parseIdentity(authCookie);
        }

        TokenIdentity authIdentity = parseIdentity(authCookie);
        if (authIdentity != null) {
            return authIdentity;
        }
        return parseIdentity(refreshCookie);
    }

    private TokenIdentity parseIdentity(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Long tenantId = jwtTokenUtil.getTenantId(token);
            Long userId = jwtTokenUtil.getUserId(token);
            if (tenantId == null || userId == null) {
                return null;
            }
            return new TokenIdentity(tenantId, userId);
        } catch (BusinessException e) {
            return null;
        }
    }

    private record TokenIdentity(Long tenantId, Long userId) {
    }

    private boolean hasAuthorizationHeader(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        return auth != null && auth.startsWith("Bearer ");
    }

    private boolean isOriginAllowed(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            String referer = request.getHeader(HttpHeaders.REFERER);
            origin = extractOrigin(referer);
        }
        if (origin == null || origin.isBlank()) {
            return false;
        }
        List<String> allowedOrigins = securityProperties.getAllowedOrigins();
        return allowedOrigins != null && allowedOrigins.contains(origin);
    }

    private String extractOrigin(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort();
            if (port > 0) {
                return uri.getScheme() + "://" + uri.getHost() + ":" + port;
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
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
