package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.TenantUser;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.entity.UserIdentity;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.mapper.UserIdentityMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.security.AuthCookieManager;
import com.sunny.datapillar.auth.security.CsrfTokenStore;
import com.sunny.datapillar.auth.security.JwtToken;
import com.sunny.datapillar.auth.security.LoginAttemptTracker;
import com.sunny.datapillar.auth.security.SessionStateStore;
import com.sunny.datapillar.auth.service.impl.LoginServiceImpl;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.service.login.LoginFinalizer;
import com.sunny.datapillar.auth.service.login.LoginMethod;
import com.sunny.datapillar.auth.service.login.LoginTokenStore;
import com.sunny.datapillar.auth.service.login.method.PasswordLoginMethod;
import com.sunny.datapillar.auth.service.login.method.SsoLoginMethod;
import com.sunny.datapillar.auth.service.login.method.sso.SsoConfigReader;
import com.sunny.datapillar.auth.service.login.method.sso.SsoStateStore;
import com.sunny.datapillar.auth.service.login.method.sso.SsoStateStore.StatePayload;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoUserInfo;
import com.sunny.datapillar.auth.service.login.method.sso.provider.SsoProvider;
import com.sunny.datapillar.auth.service.support.UserAccessReader;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.common.utils.JwtUtil;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private TenantUserMapper tenantUserMapper;
    @Mock
    private UserIdentityMapper userIdentityMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptTracker loginAttemptTracker;
    @Mock
    private SsoStateStore ssoStateStore;
    @Mock
    private SsoConfigReader ssoConfigReader;
    @Mock
    private SsoProvider ssoProvider;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private SessionStateStore sessionStateStore;
    @Mock
    private CsrfTokenStore csrfTokenStore;
    @Mock
    private AuthCookieManager authCookieManager;
    @Mock
    private UserAccessReader userAccessReader;
    @Mock
    private JwtToken jwtToken;
    @Mock
    private LoginTokenStore loginTokenStore;

    private LoginServiceImpl loginService;

    @BeforeEach
    void setUp() {
        when(ssoProvider.provider()).thenReturn("dingtalk");
        when(userAccessReader.loadRoleTypes(anyLong(), anyLong())).thenReturn(List.of());

        PasswordLoginMethod passwordLoginMethod = new PasswordLoginMethod(
                userMapper, tenantMapper, tenantUserMapper, passwordEncoder, loginAttemptTracker
        );
        SsoLoginMethod ssoLoginMethod = new SsoLoginMethod(
                ssoStateStore,
                ssoConfigReader,
                userIdentityMapper,
                userMapper,
                tenantMapper,
                List.of(ssoProvider)
        );
        LoginFinalizer loginFinalizer = new LoginFinalizer(
                tenantMapper,
                tenantUserMapper,
                jwtToken,
                sessionStateStore,
                authCookieManager,
                userAccessReader,
                loginTokenStore
        );
        loginService = new LoginServiceImpl(
                userMapper,
                tenantMapper,
                jwtUtil,
                sessionStateStore,
                csrfTokenStore,
                authCookieManager,
                loginFinalizer,
                loginTokenStore,
                ssoLoginMethod,
                List.of((LoginMethod) passwordLoginMethod, ssoLoginMethod)
        );
    }

    @Test
    void login_shouldAllowEmailAlias() {
        LoginCommand command = new LoginCommand();
        command.setMethod("password");
        command.setLoginAlias("sunny@datapillar.com");
        command.setPassword("password");
        command.setTenantCode("demo");
        command.setRememberMe(true);

        User user = new User();
        user.setId(1L);
        user.setUsername("sunny");
        user.setEmail("sunny@datapillar.com");
        user.setPasswordHash("hashed");
        user.setStatus(1);
        when(userMapper.selectByEmail("sunny@datapillar.com")).thenReturn(user);
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setCode("demo");
        tenant.setStatus(1);
        when(tenantMapper.selectByCode("demo")).thenReturn(tenant);

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(10L);
        tenantUser.setUserId(1L);
        tenantUser.setStatus(1);
        when(tenantUserMapper.selectByTenantIdAndUserId(10L, 1L)).thenReturn(tenantUser);

        AuthDto.LoginResponse loginResponse = new AuthDto.LoginResponse();
        loginResponse.setUserId(1L);
        loginResponse.setTenantId(10L);
        loginResponse.setUsername("sunny");
        loginResponse.setEmail("sunny@datapillar.com");
        when(userAccessReader.buildLoginResponse(10L, user)).thenReturn(loginResponse);

        when(jwtToken.generateAccessToken(anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn("access-token");
        when(jwtToken.generateRefreshToken(anyLong(), anyLong(), any(), anyString(), anyString()))
                .thenReturn("refresh-token");
        when(jwtToken.getAccessTokenExpiration()).thenReturn(3600L);
        when(jwtToken.getRefreshTokenExpiration(true)).thenReturn(2_592_000L);

        AuthDto.LoginResult result = loginService.login(command, "127.0.0.1", mockResponse());

        assertEquals("SUCCESS", result.getLoginStage());
        assertEquals(10L, result.getTenantId());
        assertEquals("demo", result.getTenantCode());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
    }

    @Test
    void login_shouldRejectSsoWhenNotBound() {
        LoginCommand command = buildSsoCommand();

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setCode("demo");
        tenant.setStatus(1);
        when(tenantMapper.selectById(10L)).thenReturn(tenant);
        when(ssoStateStore.consumeOrThrow("state-1", null, "dingtalk"))
                .thenReturn(new StatePayload(10L, "dingtalk"));

        SsoProviderConfig config = new SsoProviderConfig("dingtalk", null, Map.of(
                "clientId", "client",
                "clientSecret", "secret",
                "redirectUri", "https://redirect"
        ));
        when(ssoConfigReader.loadConfig(10L, "dingtalk")).thenReturn(config);

        SsoToken token = new SsoToken("access", null, Map.of());
        when(ssoProvider.exchangeCode(config, "code-1")).thenReturn(token);
        SsoUserInfo userInfo = SsoUserInfo.builder().unionId("union-1").build();
        when(ssoProvider.fetchUserInfo(config, token)).thenReturn(userInfo);
        when(ssoProvider.extractExternalUserId(userInfo)).thenReturn("union-1");
        when(userIdentityMapper.selectByProviderAndExternalUserId(10L, "dingtalk", "union-1")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> loginService.login(command, "127.0.0.1", mockResponse()));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(userMapper, never()).selectById(anyLong());
        verify(userIdentityMapper, never()).insert(any(UserIdentity.class));
    }

    @Test
    void login_shouldRejectSsoWhenStateInvalid() {
        LoginCommand command = buildSsoCommand();

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setCode("demo");
        tenant.setStatus(1);
        when(tenantMapper.selectById(10L)).thenReturn(tenant);
        when(ssoStateStore.consumeOrThrow("state-1", null, "dingtalk"))
                .thenThrow(new BusinessException(ErrorCode.SSO_STATE_INVALID));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> loginService.login(command, "127.0.0.1", mockResponse()));

        assertEquals(ErrorCode.SSO_STATE_INVALID, exception.getErrorCode());
    }

    @Test
    void login_shouldRejectSsoWhenStateExpired() {
        LoginCommand command = buildSsoCommand();

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setCode("demo");
        tenant.setStatus(1);
        when(tenantMapper.selectById(10L)).thenReturn(tenant);
        when(ssoStateStore.consumeOrThrow("state-1", null, "dingtalk"))
                .thenThrow(new BusinessException(ErrorCode.SSO_STATE_EXPIRED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> loginService.login(command, "127.0.0.1", mockResponse()));

        assertEquals(ErrorCode.SSO_STATE_EXPIRED, exception.getErrorCode());
    }

    @Test
    void login_shouldRejectSsoWhenStateReplayed() {
        LoginCommand command = buildSsoCommand();

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setCode("demo");
        tenant.setStatus(1);
        when(tenantMapper.selectById(10L)).thenReturn(tenant);
        when(ssoStateStore.consumeOrThrow("state-1", null, "dingtalk"))
                .thenThrow(new BusinessException(ErrorCode.SSO_STATE_REPLAYED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> loginService.login(command, "127.0.0.1", mockResponse()));

        assertEquals(ErrorCode.SSO_STATE_REPLAYED, exception.getErrorCode());
    }

    private LoginCommand buildSsoCommand() {
        LoginCommand command = new LoginCommand();
        command.setMethod("sso");
        command.setProvider("dingtalk");
        command.setCode("code-1");
        command.setState("state-1");
        return command;
    }

    private HttpServletResponse mockResponse() {
        return org.mockito.Mockito.mock(HttpServletResponse.class);
    }
}
