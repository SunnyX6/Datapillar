package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.TenantUser;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.OrgUserMapper;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.mapper.UserIdentityMapper;
import com.sunny.datapillar.auth.mapper.UserInvitationMapper;
import com.sunny.datapillar.auth.mapper.UserInvitationOrgMapper;
import com.sunny.datapillar.auth.mapper.UserInvitationRoleMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.mapper.UserRoleMapper;
import com.sunny.datapillar.auth.security.AuthSecurityProperties;
import com.sunny.datapillar.auth.security.CsrfTokenService;
import com.sunny.datapillar.auth.security.LoginAttemptService;
import com.sunny.datapillar.auth.security.RefreshTokenStore;
import com.sunny.datapillar.auth.security.JwtTokenUtil;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private TenantUserMapper tenantUserMapper;

    @Mock
    private UserInvitationMapper userInvitationMapper;

    @Mock
    private UserInvitationOrgMapper userInvitationOrgMapper;

    @Mock
    private UserInvitationRoleMapper userInvitationRoleMapper;

    @Mock
    private OrgUserMapper orgUserMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private UserIdentityMapper userIdentityMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private CsrfTokenService csrfTokenService;

    @Mock
    private AuthSecurityProperties securityProperties;

    @InjectMocks
    private AuthService authService;

    @Test
    void validateToken_shouldReturnSuccess() {
        AuthDto.TokenRequest request = new AuthDto.TokenRequest();
        request.setToken("token");

        Claims claims = buildClaims("access", new Date(System.currentTimeMillis() + 60000), "1", "sunny", "sunny@datapillar.com", 10L);
        when(jwtTokenUtil.parseToken("token")).thenReturn(claims);
        when(jwtTokenUtil.getTenantId("token")).thenReturn(10L);
        when(jwtTokenUtil.extractTokenSignature("token")).thenReturn("sig");

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setStatus(1);
        when(tenantMapper.selectById(10L)).thenReturn(tenant);

        User user = new User();
        user.setId(1L);
        user.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(10L);
        tenantUser.setUserId(1L);
        tenantUser.setStatus(1);
        tenantUser.setTokenSign("sig");
        tenantUser.setTokenExpireTime(java.time.LocalDateTime.now().plusMinutes(5));
        when(tenantUserMapper.selectByTenantIdAndUserId(10L, 1L)).thenReturn(tenantUser);

        AuthDto.TokenResponse response = authService.validateToken(request);

        assertTrue(response.isValid());
        assertEquals(1L, response.getUserId());
        assertEquals(10L, response.getTenantId());
        assertEquals("sunny", response.getUsername());
        assertEquals("sunny@datapillar.com", response.getEmail());
    }

    @Test
    void validateToken_shouldThrowWhenExpired() {
        AuthDto.TokenRequest request = new AuthDto.TokenRequest();
        request.setToken("token");

        when(jwtTokenUtil.parseToken("token")).thenThrow(new BusinessException(ErrorCode.TOKEN_EXPIRED));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.validateToken(request));

        assertEquals(ErrorCode.TOKEN_EXPIRED, exception.getErrorCode());
    }

    @Test
    void getTokenInfo_shouldThrowWhenBlankToken() {
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.getTokenInfo(""));

        assertEquals(ErrorCode.TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void login_shouldRequireInviteForNewUser() {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setTenantCode("demo");
        request.setUsername("alice");
        request.setPassword("password");

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setStatus(1);
        when(tenantMapper.selectByCode("demo")).thenReturn(tenant);

        HttpServletResponse response = mock(HttpServletResponse.class);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1", response));

        assertEquals(ErrorCode.INVITE_REQUIRED, exception.getErrorCode());
    }

    @Test
    void login_shouldIssueCsrfWithRememberRefreshTtl() {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setTenantCode("demo");
        request.setUsername("sunny");
        request.setPassword("password");
        request.setRememberMe(true);

        User user = new User();
        user.setId(1L);
        user.setUsername("sunny");
        user.setEmail("sunny@datapillar.com");
        user.setPasswordHash("hashed-password");
        user.setStatus(1);
        when(userMapper.selectByUsername("sunny")).thenReturn(user);
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setStatus(1);
        when(tenantMapper.selectByCode("demo")).thenReturn(tenant);

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(10L);
        tenantUser.setUserId(1L);
        tenantUser.setStatus(1);
        when(tenantUserMapper.selectByTenantIdAndUserId(10L, 1L)).thenReturn(tenantUser);

        when(userMapper.selectRolesByUserId(10L, 1L)).thenReturn(new ArrayList<>());
        when(userMapper.selectMenusByUserId(10L, 1L)).thenReturn(new ArrayList<>());

        when(jwtTokenUtil.generateAccessToken(1L, 10L, "sunny", "sunny@datapillar.com")).thenReturn("access-token");
        when(jwtTokenUtil.generateRefreshToken(1L, 10L, true)).thenReturn("refresh-token");
        when(jwtTokenUtil.extractTokenSignature("access-token")).thenReturn("sig");
        when(jwtTokenUtil.getAccessTokenExpiration()).thenReturn(3600L);
        when(jwtTokenUtil.getRefreshTokenExpiration(true)).thenReturn(2_592_000L);

        when(tenantUserMapper.updateTokenSign(eq(10L), eq(1L), eq("sig"), any())).thenReturn(1);

        AuthSecurityProperties.Csrf csrf = new AuthSecurityProperties.Csrf();
        csrf.setEnabled(true);
        csrf.setCookieName("csrf-token");
        csrf.setRefreshCookieName("refresh-csrf-token");
        when(securityProperties.getCsrf()).thenReturn(csrf);
        when(csrfTokenService.issueToken(10L, 1L, 3600L)).thenReturn("csrf-token-value");
        when(csrfTokenService.issueRefreshToken(10L, 1L, 2_592_000L)).thenReturn("refresh-csrf-token-value");

        HttpServletResponse response = mock(HttpServletResponse.class);

        AuthDto.LoginResult loginResult = authService.login(request, "127.0.0.1", response);

        assertEquals("SUCCESS", loginResult.getLoginStage());
        verify(refreshTokenStore).store(10L, 1L, "refresh-token", 2_592_000L);
        verify(csrfTokenService).issueToken(10L, 1L, 3600L);
        verify(csrfTokenService).issueRefreshToken(10L, 1L, 2_592_000L);
    }

    @Test
    void refreshToken_shouldIssueCsrfWithRememberRefreshTtl() {
        String oldRefreshToken = "old-refresh-token";

        when(jwtTokenUtil.validateToken(oldRefreshToken)).thenReturn(true);
        when(jwtTokenUtil.getTokenType(oldRefreshToken)).thenReturn("refresh");
        when(jwtTokenUtil.getUserId(oldRefreshToken)).thenReturn(1L);
        when(jwtTokenUtil.getTenantId(oldRefreshToken)).thenReturn(10L);
        when(jwtTokenUtil.getRememberMe(oldRefreshToken)).thenReturn(true);
        when(refreshTokenStore.validate(10L, 1L, oldRefreshToken)).thenReturn(true);

        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setStatus(1);
        when(tenantMapper.selectById(10L)).thenReturn(tenant);

        User user = new User();
        user.setId(1L);
        user.setUsername("sunny");
        user.setEmail("sunny@datapillar.com");
        user.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(10L);
        tenantUser.setUserId(1L);
        tenantUser.setStatus(1);
        when(tenantUserMapper.selectByTenantIdAndUserId(10L, 1L)).thenReturn(tenantUser);

        when(jwtTokenUtil.generateAccessToken(1L, 10L, "sunny", "sunny@datapillar.com")).thenReturn("new-access-token");
        when(jwtTokenUtil.generateRefreshToken(1L, 10L, true)).thenReturn("new-refresh-token");
        when(jwtTokenUtil.extractTokenSignature("new-access-token")).thenReturn("new-sig");
        when(jwtTokenUtil.getAccessTokenExpiration()).thenReturn(3600L);
        when(jwtTokenUtil.getRefreshTokenExpiration(true)).thenReturn(2_592_000L);

        when(tenantUserMapper.updateTokenSign(eq(10L), eq(1L), eq("new-sig"), any())).thenReturn(1);

        AuthSecurityProperties.Csrf csrf = new AuthSecurityProperties.Csrf();
        csrf.setEnabled(true);
        csrf.setCookieName("csrf-token");
        csrf.setRefreshCookieName("refresh-csrf-token");
        when(securityProperties.getCsrf()).thenReturn(csrf);
        when(csrfTokenService.issueToken(10L, 1L, 3600L)).thenReturn("csrf-token-value");
        when(csrfTokenService.issueRefreshToken(10L, 1L, 2_592_000L)).thenReturn("refresh-csrf-token-value");

        HttpServletResponse response = mock(HttpServletResponse.class);

        AuthDto.LoginResponse loginResponse = authService.refreshToken(oldRefreshToken, response);

        assertEquals(1L, loginResponse.getUserId());
        assertEquals(10L, loginResponse.getTenantId());
        verify(refreshTokenStore).store(10L, 1L, "new-refresh-token", 2_592_000L);
        verify(csrfTokenService).issueToken(10L, 1L, 3600L);
        verify(csrfTokenService).issueRefreshToken(10L, 1L, 2_592_000L);
    }

    private Claims buildClaims(String tokenType, Date expiration, String subject, String username, String email, Long tenantId) {
        return Jwts.claims()
                .setSubject(subject)
                .setExpiration(expiration)
                .add("tokenType", tokenType)
                .add("username", username)
                .add("email", email)
                .add("tenantId", tenantId)
                .build();
    }
}
