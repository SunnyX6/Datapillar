package com.sunny.datapillar.openlineage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.openlineage.handler.SecurityExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class OpenLineageAuthFilterTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldRejectWhenTrustedTenantIdMissing() throws ServletException, IOException {
    OpenLineageAuthFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openlineage");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://datapillar-auth.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "subject-1");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-1001");
    request.addHeader(HeaderConstants.HEADER_USER_ID, "101");

    filter.doFilter(
        request,
        response,
        chain(
            () -> {
              chainInvoked.set(true);
            }));

    Assertions.assertEquals(401, response.getStatus());
    Assertions.assertFalse(chainInvoked.get());
  }

  @Test
  void shouldRejectWhenTrustedUserIdMissing() throws ServletException, IOException {
    OpenLineageAuthFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openlineage");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://datapillar-auth.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "subject-2");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-2002");
    request.addHeader(HeaderConstants.HEADER_TENANT_ID, "2002");

    filter.doFilter(
        request,
        response,
        chain(
            () -> {
              chainInvoked.set(true);
            }));

    Assertions.assertEquals(401, response.getStatus());
    Assertions.assertFalse(chainInvoked.get());
  }

  @Test
  void shouldAttachTrustedIdentityWhenAllHeadersProvided() throws ServletException, IOException {
    OpenLineageAuthFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openlineage");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://datapillar-auth.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "subject-3");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-3003");
    request.addHeader(HeaderConstants.HEADER_TENANT_ID, "3003");
    request.addHeader(HeaderConstants.HEADER_USER_ID, "303");
    request.addHeader(HeaderConstants.HEADER_USERNAME, "sunny");
    request.addHeader(HeaderConstants.HEADER_USER_ROLES, "admin,developer");

    filter.doFilter(
        request,
        response,
        chain(
            () -> {
              chainInvoked.set(true);
            }));

    Assertions.assertEquals(200, response.getStatus());
    Assertions.assertTrue(chainInvoked.get());
    Assertions.assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    TrustedIdentityContext context = TrustedIdentityContext.current(request);
    Assertions.assertNotNull(context);
    Assertions.assertEquals(303L, context.userId());
    Assertions.assertEquals(3003L, context.tenantId());
    Assertions.assertEquals("t-3003", context.tenantCode());
  }

  private OpenLineageAuthFilter createFilter(boolean enabled) {
    TrustedIdentityProperties properties = new TrustedIdentityProperties();
    properties.setEnabled(enabled);
    SecurityExceptionHandler securityExceptionHandler =
        new SecurityExceptionHandler(new ObjectMapper());
    return new OpenLineageAuthFilter(properties, securityExceptionHandler);
  }

  private FilterChain chain(Runnable callback) {
    return (request, response) -> callback.run();
  }
}
