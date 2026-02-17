package com.sunny.datapillar.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 认证CookieManager组件
 * 负责认证CookieManager核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class AuthCookieManager {

    private final JwtToken jwtToken;
    private final CsrfTokenStore csrfTokenStore;
    private final AuthSecurityProperties securityProperties;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken, Boolean rememberMe) {
        int accessMaxAge = Math.toIntExact(jwtToken.getAccessTokenExpiration());
        int refreshMaxAge = Math.toIntExact(jwtToken.getRefreshTokenExpiration(Boolean.TRUE.equals(rememberMe)));

        ResponseCookie accessTokenCookie = ResponseCookie.from("auth-token", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(accessMaxAge)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, accessTokenCookie);

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh-token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(refreshMaxAge)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, refreshTokenCookie);
    }

    public void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        int accessMaxAge = Math.toIntExact(jwtToken.getAccessTokenExpiration());
        ResponseCookie accessTokenCookie = ResponseCookie.from("auth-token", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(accessMaxAge)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, accessTokenCookie);
    }

    public void setLoginTokenCookie(HttpServletResponse response, String loginToken, long ttlSeconds) {
        ResponseCookie loginTokenCookie = ResponseCookie.from("login-token", loginToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/login")
                .maxAge(ttlSeconds)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, loginTokenCookie);
    }

    public void issueBusinessCsrfCookie(Long tenantId, Long userId, long ttlSeconds, HttpServletResponse response) {
        if (!securityProperties.getCsrf().isEnabled()) {
            return;
        }
        String token = csrfTokenStore.issueToken(tenantId, userId, ttlSeconds);
        String cookieName = securityProperties.getCsrf().getCookieName();
        ResponseCookie csrfCookie = ResponseCookie.from(cookieName, token)
                .httpOnly(false)
                .secure(cookieSecure)
                .path("/")
                .maxAge(ttlSeconds)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, csrfCookie);
    }

    public void issueSessionCsrfCookies(Long tenantId, Long userId, long refreshTtlSeconds, HttpServletResponse response) {
        long accessTtlSeconds = jwtToken.getAccessTokenExpiration();
        issueBusinessCsrfCookie(tenantId, userId, accessTtlSeconds, response);
        issueRefreshCsrfCookie(tenantId, userId, refreshTtlSeconds, response);
    }

    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessTokenCookie = ResponseCookie.from("auth-token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, accessTokenCookie);

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh-token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, refreshTokenCookie);

        String csrfCookieName = securityProperties.getCsrf().getCookieName();
        ResponseCookie csrfCookie = ResponseCookie.from(csrfCookieName, "")
                .httpOnly(false)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, csrfCookie);

        String refreshCsrfCookieName = securityProperties.getCsrf().getRefreshCookieName();
        ResponseCookie refreshCsrfCookie = ResponseCookie.from(refreshCsrfCookieName, "")
                .httpOnly(false)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, refreshCsrfCookie);
    }

    public void clearLoginTokenCookie(HttpServletResponse response) {
        ResponseCookie loginTokenCookie = ResponseCookie.from("login-token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/login")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, loginTokenCookie);
    }

    private void issueRefreshCsrfCookie(Long tenantId, Long userId, long ttlSeconds, HttpServletResponse response) {
        if (!securityProperties.getCsrf().isEnabled()) {
            return;
        }
        String token = csrfTokenStore.issueRefreshToken(tenantId, userId, ttlSeconds);
        String cookieName = securityProperties.getCsrf().getRefreshCookieName();
        ResponseCookie csrfCookie = ResponseCookie.from(cookieName, token)
                .httpOnly(false)
                .secure(cookieSecure)
                .path("/")
                .maxAge(ttlSeconds)
                .sameSite("Strict")
                .build();
        addCookieHeader(response, csrfCookie);
    }

    private void addCookieHeader(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
