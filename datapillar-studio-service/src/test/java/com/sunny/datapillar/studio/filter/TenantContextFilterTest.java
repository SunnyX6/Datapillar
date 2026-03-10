package com.sunny.datapillar.studio.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import com.sunny.datapillar.studio.security.TrustedIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantContextFilterTest {

  @AfterEach
  void clearTenantContext() {
    TenantContextHolder.clear();
  }

  @Test
  void shouldRejectWhenTrustedIdentityContextMissing() throws ServletException, IOException {
    TenantContextFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("trusted_identity_context_missing"));
    assertFalse(chainInvoked.get());
    assertNull(TenantContextHolder.get());
  }

  @Test
  void shouldPopulateTenantContextAndClearAfterChain() throws ServletException, IOException {
    TenantContextFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    TrustedIdentityContext.attach(
        request,
        new TrustedIdentityContext(
            PrincipalType.USER,
            "user:101",
            101L,
            1001L,
            "tenant-a",
            "sunny",
            "sunny@datapillar.ai",
            List.of("ADMIN"),
            false,
            null,
            null,
            null));
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(
        request,
        response,
        chain(
            () -> {
              chainInvoked.set(true);
              assertNotNull(TenantContextHolder.get());
              assertEquals(1001L, TenantContextHolder.getTenantId());
              assertEquals("tenant-a", TenantContextHolder.getTenantCode());
            }));

    assertEquals(200, response.getStatus());
    assertTrue(chainInvoked.get());
    assertNull(TenantContextHolder.get());
  }

  private TenantContextFilter createFilter(boolean enabled) {
    TrustedIdentityProperties properties = new TrustedIdentityProperties();
    properties.setEnabled(enabled);
    return new TenantContextFilter(properties, new SecurityExceptionHandler(new ObjectMapper()));
  }

  private FilterChain chain(Runnable callback) {
    return (request, response) -> callback.run();
  }
}
