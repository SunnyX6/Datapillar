package com.sunny.datapillar.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityExceptionHandlerTest {

    private final SecurityExceptionHandler handler = new SecurityExceptionHandler(new ObjectMapper());

    @Test
    void commence_shouldIncludeAuthenticationExceptionDetailInMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/token-info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BadCredentialsException exception = new BadCredentialsException("jwt_signature_invalid");

        handler.commence(request, response, exception);

        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("BadCredentialsException"));
        assertTrue(body.contains("jwt_signature_invalid"));
    }

    @Test
    void handle_shouldIncludeAccessDeniedExceptionDetailInMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessDeniedException exception = new AccessDeniedException("role_missing");

        handler.handle(request, response, exception);

        assertEquals(403, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("AccessDeniedException"));
        assertTrue(body.contains("role_missing"));
    }

    @Test
    void commence_shouldPreserveBaseMessageWhenDetailEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/token-info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        InsufficientAuthenticationException exception = new InsufficientAuthenticationException(null);

        handler.commence(request, response, exception);

        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("未授权访问"));
    }
}
