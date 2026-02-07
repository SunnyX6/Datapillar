package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

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
        properties.setAllowedOrigins(List.of("http://localhost:3001"));

        CsrfTokenService csrfTokenService = mock(CsrfTokenService.class);
        JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);

        AuthCsrfInterceptor interceptor = new AuthCsrfInterceptor(properties, csrfTokenService, jwtTokenUtil);

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

        when(jwtTokenUtil.getTenantId("valid-refresh-token")).thenReturn(10L);
        when(jwtTokenUtil.getUserId("valid-refresh-token")).thenReturn(1L);
        when(csrfTokenService.validateRefreshToken(10L, 1L, "refresh-csrf-token-value")).thenReturn(true);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(csrfTokenService).validateRefreshToken(10L, 1L, "refresh-csrf-token-value");
        verify(csrfTokenService, never()).validateToken(10L, 1L, "refresh-csrf-token-value");
    }

    @Test
    void preHandle_nonRefreshShouldFallbackToRefreshTokenWhenAccessExpired() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getCsrf().setEnabled(true);
        properties.getCsrf().setHeaderName("X-CSRF-Token");
        properties.getCsrf().setCookieName("csrf-token");
        properties.getCsrf().setRefreshHeaderName("X-Refresh-CSRF-Token");
        properties.getCsrf().setRefreshCookieName("refresh-csrf-token");
        properties.setAllowedOrigins(List.of("http://localhost:3001"));

        CsrfTokenService csrfTokenService = mock(CsrfTokenService.class);
        JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);

        AuthCsrfInterceptor interceptor = new AuthCsrfInterceptor(properties, csrfTokenService, jwtTokenUtil);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/auth/logout");
        request.addHeader("Origin", "http://localhost:3001");
        request.addHeader("X-CSRF-Token", "csrf-token-value");
        request.setCookies(
                new Cookie("auth-token", "expired-access-token"),
                new Cookie("refresh-token", "valid-refresh-token"),
                new Cookie("csrf-token", "csrf-token-value")
        );

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenUtil.getTenantId("expired-access-token"))
                .thenThrow(new BusinessException(ErrorCode.TOKEN_EXPIRED));
        when(jwtTokenUtil.getTenantId("valid-refresh-token")).thenReturn(10L);
        when(jwtTokenUtil.getUserId("valid-refresh-token")).thenReturn(1L);
        when(csrfTokenService.validateToken(10L, 1L, "csrf-token-value")).thenReturn(true);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(csrfTokenService).validateToken(10L, 1L, "csrf-token-value");
    }
}
