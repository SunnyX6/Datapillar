package com.sunny.datapillar.auth.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpUtilTest {

    @Test
    void getClientIp_shouldIgnoreForwardedHeadersWhenRemoteIsUntrusted() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRemoteAddr()).thenReturn("198.51.100.10");
        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
        Mockito.when(request.getHeader("X-Real-IP")).thenReturn("5.6.7.8");

        String clientIp = ClientIpUtil.getClientIp(request, List.of("10.0.0.0/8"));

        assertEquals("198.51.100.10", clientIp);
    }

    @Test
    void getClientIp_shouldUseForwardedForWhenRemoteIsTrustedProxy() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRemoteAddr()).thenReturn("10.10.0.3");
        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 10.10.0.3");

        String clientIp = ClientIpUtil.getClientIp(request, List.of("10.0.0.0/8"));

        assertEquals("1.2.3.4", clientIp);
    }

    @Test
    void getClientIp_shouldPreferRightMostNonTrustedIpWhenXffContainsSpoofedPrefix() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRemoteAddr()).thenReturn("10.10.0.3");
        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 198.51.100.9");

        String clientIp = ClientIpUtil.getClientIp(request, List.of("10.0.0.0/8"));

        assertEquals("198.51.100.9", clientIp);
    }

    @Test
    void getClientIp_shouldFallbackToRealIpWhenForwardedForMissing() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRemoteAddr()).thenReturn("10.10.0.3");
        Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        Mockito.when(request.getHeader("X-Real-IP")).thenReturn("9.8.7.6");

        String clientIp = ClientIpUtil.getClientIp(request, List.of("10.0.0.0/8"));

        assertEquals("9.8.7.6", clientIp);
    }
}
