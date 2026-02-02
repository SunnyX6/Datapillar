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

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

        when(jwtTokenUtil.parseToken("token")).thenThrow(new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.validateToken(request));

        assertEquals(ErrorCode.AUTH_TOKEN_EXPIRED, exception.getErrorCode());
    }

    @Test
    void getTokenInfo_shouldThrowWhenBlankToken() {
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.getTokenInfo(""));

        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, exception.getErrorCode());
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

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request, response));

        assertEquals(ErrorCode.AUTH_INVITE_REQUIRED, exception.getErrorCode());
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
