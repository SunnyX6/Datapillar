package com.sunny.datapillar.openlineage.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.handler.SecurityExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantContextFilterTest {

  @AfterEach
  void clearContext() {
    TenantContextHolder.clear();
    TrustedIdentityContextHolder.clear();
  }

  @Test
  void shouldRejectWhenTrustedIdentityMissing() throws ServletException, IOException {
    TenantContextFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/events");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

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
  void shouldSetTenantContextWhenTrustedIdentityPresent() throws ServletException, IOException {
    TenantContextFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/events");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    TrustedIdentityContextHolder.set(
        new TrustedIdentityContext(
            PrincipalType.USER,
            "user:101",
            101L,
            1001L,
            "t-1001",
            "sunny",
            "sunny@datapillar.ai",
            List.of("ADMIN"),
            false,
            null,
            null,
            "https://issuer",
            "subject",
            "trace-1"));

    filter.doFilter(
        request,
        response,
        chain(
            () -> {
              chainInvoked.set(true);
              Assertions.assertEquals(1001L, TenantContextHolder.getTenantId());
              Assertions.assertEquals("t-1001", TenantContextHolder.getTenantCode());
            }));

    Assertions.assertEquals(200, response.getStatus());
    Assertions.assertTrue(chainInvoked.get());
    Assertions.assertNull(TenantContextHolder.get());
    Assertions.assertNull(TrustedIdentityContextHolder.get());
  }

  private TenantContextFilter createFilter(boolean enabled) {
    TrustedIdentityProperties properties = new TrustedIdentityProperties();
    properties.setEnabled(enabled);
    SecurityExceptionHandler securityExceptionHandler =
        new SecurityExceptionHandler(new ObjectMapper());
    return new TenantContextFilter(properties, securityExceptionHandler);
  }

  private FilterChain chain(Runnable callback) {
    return (request, response) -> callback.run();
  }
}
