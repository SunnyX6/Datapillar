package com.sunny.datapillar.auth.service.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.dto.login.response.TenantOptionItem;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.TenantUser;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.key.KeySetKeyManager;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.security.AuthCookieManager;
import com.sunny.datapillar.auth.security.JwtToken;
import com.sunny.datapillar.auth.service.support.UserAccessReader;
import com.sunny.datapillar.auth.session.SessionStore;
import com.sunny.datapillar.auth.token.ClaimAssembler;
import com.sunny.datapillar.auth.token.JwtTokenEngine;
import com.sunny.datapillar.auth.token.TokenEngine;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class LoginFinalizerTest {

  @Mock private TenantMapper tenantMapper;
  @Mock private TenantUserMapper tenantUserMapper;
  @Mock private SessionStore sessionStore;
  @Mock private AuthCookieManager authCookieManager;
  @Mock private UserAccessReader userAccessReader;
  @Mock private LoginTokenStore loginTokenStore;

  private TokenEngine tokenEngine;

  @BeforeEach
  void setUp() {
    AuthProperties properties = new AuthProperties();
    properties.getToken().setIssuer("https://auth.datapillar.local");
    properties.getToken().setAudience("datapillar-api");
    properties.getToken().setKeysetPath("classpath:security/auth-token-dev-keyset.json");
    properties.getToken().setAccessTtlSeconds(3600);
    properties.getToken().setRefreshTtlSeconds(120);
    properties.getToken().setRefreshRememberTtlSeconds(240);
    properties.getToken().setLoginTtlSeconds(60);
    properties.validate();
    tokenEngine =
        new JwtTokenEngine(
            new JwtToken(properties, new KeySetKeyManager(new ObjectMapper(), properties)),
            new ClaimAssembler());
  }

  @Test
  void finalize_shouldKeepJwtAndSessionStoreJtiAligned() {
    User user = new User();
    user.setId(101L);
    user.setUsername("sunny");
    user.setEmail("sunny@datapillar.ai");
    user.setStatus(1);

    Tenant tenant = new Tenant();
    tenant.setId(1001L);
    tenant.setCode("tenant-a");
    tenant.setStatus(1);

    TenantUser tenantUser = new TenantUser();
    tenantUser.setTenantId(1001L);
    tenantUser.setUserId(101L);
    tenantUser.setStatus(1);

    when(tenantUserMapper.selectByTenantIdAndUserId(1001L, 101L)).thenReturn(tenantUser);
    when(tenantUserMapper.selectTenantOptionsByUserId(101L))
        .thenReturn(List.of(new TenantOptionItem(1001L, "tenant-a", "Tenant A", 1, 1)));
    when(userAccessReader.loadRoleTypes(1001L, 101L)).thenReturn(List.of("ADMIN"));

    LoginFinalizer finalizer =
        new LoginFinalizer(
            tenantMapper,
            tenantUserMapper,
            tokenEngine,
            sessionStore,
            authCookieManager,
            userAccessReader,
            loginTokenStore);

    MockHttpServletResponse response = new MockHttpServletResponse();
    LoginSubject subject =
        LoginSubject.builder().user(user).tenant(tenant).loginMethod("simple").build();

    finalizer.finalize(subject, false, response);

    ArgumentCaptor<String> sidCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> accessJtiCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> refreshJtiCaptor = ArgumentCaptor.forClass(String.class);
    verify(sessionStore)
        .openSession(
            sidCaptor.capture(),
            eq(1001L),
            eq(101L),
            accessJtiCaptor.capture(),
            refreshJtiCaptor.capture(),
            eq(120L),
            eq(3600L));

    ArgumentCaptor<String> accessTokenCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
    verify(authCookieManager)
        .setAuthCookies(
            any(MockHttpServletResponse.class),
            accessTokenCaptor.capture(),
            refreshTokenCaptor.capture(),
            eq(false));
    verify(authCookieManager).issueSessionCsrfCookies(1001L, 101L, 120L, response);

    Claims accessClaims = tokenEngine.verify(accessTokenCaptor.getValue());
    Claims refreshClaims = tokenEngine.verify(refreshTokenCaptor.getValue());

    assertEquals(sidCaptor.getValue(), accessClaims.get("sid", String.class));
    assertEquals(sidCaptor.getValue(), refreshClaims.get("sid", String.class));
    assertEquals(accessJtiCaptor.getValue(), accessClaims.getId());
    assertEquals(refreshJtiCaptor.getValue(), refreshClaims.getId());
  }
}
