package com.sunny.datapillar.studio.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.security.PrincipalType;
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
import org.springframework.security.core.context.SecurityContextHolder;

class TrustedIdentityAuthenticationFilterTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldRejectWhenPrincipalHeadersMissing() throws ServletException, IOException {
    TrustedIdentityAuthenticationFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("principal_header_missing"));
    assertFalse(chainInvoked.get());
    assertNull(TrustedIdentityContext.current(request));
  }

  @Test
  void shouldRejectWhenTrustedUserContextMissing() throws ServletException, IOException {
    TrustedIdentityAuthenticationFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://auth.datapillar.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "subject-101");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_TYPE, PrincipalType.USER.name());
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ID, "user:101");
    request.addHeader(HeaderConstants.HEADER_TENANT_ID, "1001");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-1001");
    request.addHeader(HeaderConstants.HEADER_USER_ROLES, "ADMIN");

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("trusted_user_context_missing"));
    assertFalse(chainInvoked.get());
    assertNull(TrustedIdentityContext.current(request));
  }

  @Test
  void shouldRejectWhenRoleHeaderMissing() throws ServletException, IOException {
    TrustedIdentityAuthenticationFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://auth.datapillar.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "subject-101");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_TYPE, PrincipalType.USER.name());
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ID, "user:101");
    request.addHeader(HeaderConstants.HEADER_USER_ID, "101");
    request.addHeader(HeaderConstants.HEADER_TENANT_ID, "1001");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-1001");
    request.addHeader(HeaderConstants.HEADER_USERNAME, "sunny");
    request.addHeader(HeaderConstants.HEADER_EMAIL, "sunny@datapillar.ai");

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("user_roles_missing"));
    assertFalse(chainInvoked.get());
    assertNull(TrustedIdentityContext.current(request));
  }

  @Test
  void shouldAttachTrustedIdentityContextWhenHeadersValid() throws ServletException, IOException {
    TrustedIdentityAuthenticationFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://auth.datapillar.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "subject-101");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_TYPE, PrincipalType.USER.name());
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ID, "user:101");
    request.addHeader(HeaderConstants.HEADER_USER_ID, "101");
    request.addHeader(HeaderConstants.HEADER_TENANT_ID, "1001");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-1001");
    request.addHeader(HeaderConstants.HEADER_USERNAME, "sunny");
    request.addHeader(HeaderConstants.HEADER_EMAIL, "sunny@datapillar.ai");
    request.addHeader(HeaderConstants.HEADER_USER_ROLES, "ADMIN, USER");
    request.addHeader(HeaderConstants.HEADER_TRACE_ID, "trace-123");
    request.addHeader(HeaderConstants.HEADER_IMPERSONATION, "false");

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(200, response.getStatus());
    assertTrue(chainInvoked.get());
    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    TrustedIdentityContext context = TrustedIdentityContext.current(request);
    assertNotNull(context);
    assertEquals(PrincipalType.USER, context.principalType());
    assertEquals("user:101", context.principalId());
    assertEquals(101L, context.userId());
    assertEquals(1001L, context.tenantId());
    assertEquals("t-1001", context.tenantCode());
    assertEquals("sunny", context.username());
    assertEquals("sunny@datapillar.ai", context.email());
    assertIterableEquals(List.of("ADMIN", "USER"), context.roles());
    assertFalse(context.impersonation());
    assertTrue(
        SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .anyMatch(authority -> "ADMIN".equals(authority.getAuthority())));
  }

  @Test
  void shouldAttachImpersonationContextWhenHeadersValid() throws ServletException, IOException {
    TrustedIdentityAuthenticationFilter filter = createFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/projects");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://auth.datapillar.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "subject-101");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_TYPE, PrincipalType.USER.name());
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ID, "user:101");
    request.addHeader(HeaderConstants.HEADER_USER_ID, "101");
    request.addHeader(HeaderConstants.HEADER_TENANT_ID, "1001");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-1001");
    request.addHeader(HeaderConstants.HEADER_USERNAME, "sunny");
    request.addHeader(HeaderConstants.HEADER_USER_ROLES, "ADMIN");
    request.addHeader(HeaderConstants.HEADER_IMPERSONATION, "true");
    request.addHeader(HeaderConstants.HEADER_ACTOR_USER_ID, "101");
    request.addHeader(HeaderConstants.HEADER_ACTOR_TENANT_ID, "0");

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(200, response.getStatus());
    assertTrue(chainInvoked.get());
    TrustedIdentityContext context = TrustedIdentityContext.current(request);
    assertNotNull(context);
    assertTrue(context.impersonation());
    assertEquals(101L, context.actorUserId());
    assertEquals(0L, context.actorTenantId());
  }

  @Test
  void shouldAttachApiKeyPrincipalWhenHeadersValid() throws ServletException, IOException {
    TrustedIdentityAuthenticationFilter filter = createFilter(true);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/studio/admin/tenant/current/api-keys");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ISS, "https://auth.datapillar.local");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_SUB, "api-key:201");
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_TYPE, PrincipalType.API_KEY.name());
    request.addHeader(HeaderConstants.HEADER_PRINCIPAL_ID, "api-key:201");
    request.addHeader(HeaderConstants.HEADER_TENANT_ID, "1001");
    request.addHeader(HeaderConstants.HEADER_TENANT_CODE, "t-1001");
    request.addHeader(HeaderConstants.HEADER_USERNAME, "lineage-ingest");
    request.addHeader(HeaderConstants.HEADER_USER_ROLES, "ADMIN");

    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean(false);

    filter.doFilter(request, response, chain(() -> chainInvoked.set(true)));

    assertEquals(200, response.getStatus());
    assertTrue(chainInvoked.get());
    TrustedIdentityContext context = TrustedIdentityContext.current(request);
    assertNotNull(context);
    assertEquals(PrincipalType.API_KEY, context.principalType());
    assertEquals("api-key:201", context.principalId());
    assertNull(context.userId());
    assertEquals(1001L, context.tenantId());
    assertEquals("lineage-ingest", context.username());
  }

  private TrustedIdentityAuthenticationFilter createFilter(boolean enabled) {
    TrustedIdentityProperties properties = new TrustedIdentityProperties();
    properties.setEnabled(enabled);
    return new TrustedIdentityAuthenticationFilter(
        properties, new SecurityExceptionHandler(new ObjectMapper()));
  }

  private FilterChain chain(Runnable callback) {
    return (request, response) -> callback.run();
  }
}
