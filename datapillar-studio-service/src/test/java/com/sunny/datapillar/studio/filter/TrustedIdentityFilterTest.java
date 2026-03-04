package com.sunny.datapillar.studio.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.entity.UserIdentity;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserIdentityMapper;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import com.sunny.datapillar.studio.security.TrustedIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TrustedIdentityFilterTest {

  @Mock private UserIdentityMapper userIdentityMapper;
  @Mock private TenantMapper tenantMapper;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldRejectWhenPrincipalHeadersMissing() throws ServletException, IOException {
    TrustedIdentityFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(
        request,
        response,
        chain(
            () -> {
              chainInvoked.set(true);
            }));

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("principal_header_missing"));
    assertFalse(chainInvoked.get());
    assertNull(TrustedIdentityContext.current(request));
  }

  @Test
  void shouldAttachTrustedIdentityContextWhenHeadersValid() throws ServletException, IOException {
    TrustedIdentityFilter filter = createFilter(true);

    UserIdentity identity = new UserIdentity();
    identity.setUserId(101L);
    when(userIdentityMapper.selectOne(any())).thenReturn(identity);

    Tenant tenant = new Tenant();
    tenant.setId(1001L);
    tenant.setCode("t-1001");
    tenant.setStatus(1);
    when(tenantMapper.selectByCode("t-1001")).thenReturn(tenant);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://auth.datapillar.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "subject-101");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-1001");
    request.addHeader(HeaderConstants.HEADER_USERNAME, "sunny");
    request.addHeader(HeaderConstants.HEADER_EMAIL, "sunny@datapillar.ai");
    request.addHeader(HeaderConstants.HEADER_TRACE_ID, "trace-123");

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(
        request,
        response,
        chain(
            () -> {
              chainInvoked.set(true);
            }));

    assertEquals(200, response.getStatus());
    assertTrue(chainInvoked.get());
    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    TrustedIdentityContext context = TrustedIdentityContext.current(request);
    assertNotNull(context);
    assertEquals(101L, context.userId());
    assertEquals(1001L, context.tenantId());
    assertEquals("t-1001", context.tenantCode());
    assertEquals("sunny", context.username());
  }

  private TrustedIdentityFilter createFilter(boolean enabled) {
    TrustedIdentityProperties properties = new TrustedIdentityProperties();
    properties.setEnabled(enabled);
    return new TrustedIdentityFilter(
        properties,
        userIdentityMapper,
        tenantMapper,
        new SecurityExceptionHandler(new ObjectMapper()));
  }

  private FilterChain chain(Runnable callback) {
    return (request, response) -> callback.run();
  }
}
