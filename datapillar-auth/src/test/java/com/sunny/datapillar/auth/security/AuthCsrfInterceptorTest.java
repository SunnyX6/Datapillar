package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthCsrfInterceptorTest {

    @Test
    void preHandle_refreshShouldFallbackToRefreshTokenWhenAccessExpired() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getCsrf().setEnabled(true);
        properties.getCsrf().setHeaderName("X-CSRF-Token");
        properties.getCsrf().setCookieName("csrf-token");
        properties.getCsrf().setRefreshHeaderName("X-Refresh-CSRF-Token");
        properties.getCsrf().setRefreshCookieName("refresh-csrf-token");

        CsrfTokenStore csrfTokenStore = mock(CsrfTokenStore.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);

        AuthCsrfInterceptor interceptor = new AuthCsrfInterceptor(properties, csrfTokenStore, jwtUtil);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/auth/refresh");
        request.addHeader("Origin", "http://localhost:3001");
        request.addHeader("X-Refresh-CSRF-Token", "refresh-csrf-token-value");
        request.setCookies(
                new Cookie("auth-token", "expired-access-token"),
                new Cookie("refresh-token", "valid-refresh-token"),
                new Cookie("refresh-csrf-token", "refresh-csrf-token-value")
        );

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims refreshClaims = Jwts.claims().setSubject("1").add("tenantId", 10L).build();
        when(jwtUtil.parseToken("valid-refresh-token")).thenReturn(refreshClaims);
        when(jwtUtil.getTenantId(refreshClaims)).thenReturn(10L);
        when(jwtUtil.getUserId(refreshClaims)).thenReturn(1L);
        when(csrfTokenStore.validateRefreshToken(10L, 1L, "refresh-csrf-token-value")).thenReturn(true);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(csrfTokenStore).validateRefreshToken(10L, 1L, "refresh-csrf-token-value");
        verify(csrfTokenStore, never()).validateToken(10L, 1L, "refresh-csrf-token-value");
    }

    @Test
    void preHandle_nonRefreshShouldFallbackToRefreshTokenWhenAccessExpired() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getCsrf().setEnabled(true);
        properties.getCsrf().setHeaderName("X-CSRF-Token");
        properties.getCsrf().setCookieName("csrf-token");
        properties.getCsrf().setRefreshHeaderName("X-Refresh-CSRF-Token");
        properties.getCsrf().setRefreshCookieName("refresh-csrf-token");

        CsrfTokenStore csrfTokenStore = mock(CsrfTokenStore.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);

        AuthCsrfInterceptor interceptor = new AuthCsrfInterceptor(properties, csrfTokenStore, jwtUtil);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/auth/revoke");
        request.addHeader("Origin", "http://localhost:3001");
        request.addHeader("X-CSRF-Token", "csrf-token-value");
        request.setCookies(
                new Cookie("auth-token", "expired-access-token"),
                new Cookie("refresh-token", "valid-refresh-token"),
                new Cookie("csrf-token", "csrf-token-value")
        );

        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims refreshClaims = Jwts.claims().setSubject("1").add("tenantId", 10L).build();
        when(jwtUtil.parseToken("expired-access-token"))
                .thenThrow(new UnauthorizedException("Token已过期"));
        when(jwtUtil.parseToken("valid-refresh-token")).thenReturn(refreshClaims);
        when(jwtUtil.getTenantId(refreshClaims)).thenReturn(10L);
        when(jwtUtil.getUserId(refreshClaims)).thenReturn(1L);
        when(csrfTokenStore.validateToken(10L, 1L, "csrf-token-value")).thenReturn(true);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(csrfTokenStore).validateToken(10L, 1L, "csrf-token-value");
    }
}
