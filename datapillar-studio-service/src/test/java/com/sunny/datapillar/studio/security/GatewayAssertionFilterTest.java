package com.sunny.datapillar.studio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.studio.filter.GatewayAssertionFilter;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class GatewayAssertionFilterTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldRejectWhenAssertionMissing() throws Exception {
        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setHeaderName(HeaderConstants.HEADER_GATEWAY_ASSERTION);

        GatewayAssertionVerifier verifier = Mockito.mock(GatewayAssertionVerifier.class);
        SecurityExceptionHandler securityExceptionHandler = new SecurityExceptionHandler(new ObjectMapper());
        GatewayAssertionFilter filter = new GatewayAssertionFilter(properties, verifier, securityExceptionHandler);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/studio/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> called.set(true);
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(!called.get());
    }

    @Test
    void doFilter_shouldRejectForgedLegacyHeadersWithoutAssertion() throws Exception {
        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setHeaderName(HeaderConstants.HEADER_GATEWAY_ASSERTION);

        GatewayAssertionVerifier verifier = Mockito.mock(GatewayAssertionVerifier.class);
        SecurityExceptionHandler securityExceptionHandler = new SecurityExceptionHandler(new ObjectMapper());
        GatewayAssertionFilter filter = new GatewayAssertionFilter(properties, verifier, securityExceptionHandler);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/studio/projects");
        request.addHeader(HeaderConstants.HEADER_USER_ID, "1");
        request.addHeader(HeaderConstants.HEADER_TENANT_ID, "10");
        request.addHeader(HeaderConstants.HEADER_IMPERSONATION, "true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> called.set(true);
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(!called.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_shouldPopulateSecurityContextWhenAssertionValid() throws Exception {
        GatewayAssertionProperties properties = new GatewayAssertionProperties();
        properties.setEnabled(true);
        properties.setHeaderName(HeaderConstants.HEADER_GATEWAY_ASSERTION);

        GatewayAssertionVerifier verifier = Mockito.mock(GatewayAssertionVerifier.class);
        GatewayAssertionContext context = new GatewayAssertionContext(
                1L,
                10L,
                "sunny",
                "sunny@datapillar.test",
                List.of("ADMIN"),
                false,
                null,
                null,
                "assertion-jti"
        );
        when(verifier.verify(anyString(), anyString(), anyString())).thenReturn(context);

        SecurityExceptionHandler securityExceptionHandler = new SecurityExceptionHandler(new ObjectMapper());
        GatewayAssertionFilter filter = new GatewayAssertionFilter(properties, verifier, securityExceptionHandler);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/studio/projects");
        request.addHeader(HeaderConstants.HEADER_GATEWAY_ASSERTION, "signed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> called.set(true);
        filter.doFilter(request, response, chain);

        assertTrue(called.get());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("sunny", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(context, request.getAttribute(GatewayAssertionContext.REQUEST_ATTRIBUTE));
        assertEquals(200, response.getStatus());
    }
}
