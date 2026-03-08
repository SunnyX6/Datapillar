package com.sunny.datapillar.studio.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.handler.SecurityExceptionHandler;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.security.TrustedIdentityContext;
import com.sunny.datapillar.studio.security.TrustedIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class IdentityStateValidationFilterTest {

  @Mock private UserMapper userMapper;
  @Mock private TenantUserMapper tenantUserMapper;
  @Mock private TenantMapper tenantMapper;

  @Test
  void shouldRejectWhenTrustedIdentityContextMissing() throws ServletException, IOException {
    IdentityStateValidationFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("trusted_identity_context_missing"));
    assertFalse(chainInvoked.get());
    verifyNoInteractions(userMapper, tenantUserMapper, tenantMapper);
  }

  @Test
  void shouldRejectWhenTenantMembershipMissing() throws ServletException, IOException {
    IdentityStateValidationFilter filter = createFilter(true);
    MockHttpServletRequest request = requestWithTrustedIdentity(false);

    User user = new User();
    user.setId(101L);
    user.setStatus(1);
    when(userMapper.selectById(101L)).thenReturn(user);

    Tenant tenant = new Tenant();
    tenant.setId(1001L);
    tenant.setCode("tenant-a");
    tenant.setStatus(1);
    when(tenantMapper.selectByCode("tenant-a")).thenReturn(tenant);
    when(tenantUserMapper.selectByTenantIdAndUserId(1001L, 101L)).thenReturn(null);

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("tenant_membership_missing"));
    assertFalse(chainInvoked.get());
  }

  @Test
  void shouldSkipMembershipLookupWhenImpersonation() throws ServletException, IOException {
    IdentityStateValidationFilter filter = createFilter(true);
    MockHttpServletRequest request = requestWithTrustedIdentity(true);

    User user = new User();
    user.setId(101L);
    user.setStatus(1);
    when(userMapper.selectById(101L)).thenReturn(user);

    Tenant tenant = new Tenant();
    tenant.setId(1001L);
    tenant.setCode("tenant-a");
    tenant.setStatus(1);
    when(tenantMapper.selectByCode("tenant-a")).thenReturn(tenant);

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(200, response.getStatus());
    assertTrue(chainInvoked.get());
    verifyNoInteractions(tenantUserMapper);
  }

  @Test
  void shouldAllowWhenLocalIdentityStateValid() throws ServletException, IOException {
    IdentityStateValidationFilter filter = createFilter(true);
    MockHttpServletRequest request = requestWithTrustedIdentity(false);

    User user = new User();
    user.setId(101L);
    user.setStatus(1);
    when(userMapper.selectById(101L)).thenReturn(user);

    Tenant tenant = new Tenant();
    tenant.setId(1001L);
    tenant.setCode("tenant-a");
    tenant.setStatus(1);
    when(tenantMapper.selectByCode("tenant-a")).thenReturn(tenant);

    TenantUser tenantUser = new TenantUser();
    tenantUser.setTenantId(1001L);
    tenantUser.setUserId(101L);
    tenantUser.setStatus(1);
    when(tenantUserMapper.selectByTenantIdAndUserId(1001L, 101L)).thenReturn(tenantUser);

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(200, response.getStatus());
    assertTrue(chainInvoked.get());
  }

  private IdentityStateValidationFilter createFilter(boolean enabled) {
    TrustedIdentityProperties properties = new TrustedIdentityProperties();
    properties.setEnabled(enabled);
    return new IdentityStateValidationFilter(
        properties,
        userMapper,
        tenantUserMapper,
        tenantMapper,
        new SecurityExceptionHandler(new ObjectMapper()));
  }

  private MockHttpServletRequest requestWithTrustedIdentity(boolean impersonation) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    TrustedIdentityContext.attach(
        request,
        new TrustedIdentityContext(
            101L,
            1001L,
            "tenant-a",
            "sunny",
            "sunny@datapillar.ai",
            List.of("ADMIN"),
            impersonation,
            impersonation ? 101L : null,
            impersonation ? 0L : null,
            null));
    return request;
  }

  private FilterChain chain(Runnable callback) {
    return (request, response) -> callback.run();
  }
}
