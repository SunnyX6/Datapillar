package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.TenantUser;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.TenantUserMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.security.AuthCookieManager;
import com.sunny.datapillar.auth.security.JwtToken;
import com.sunny.datapillar.auth.security.SessionStateStore;
import com.sunny.datapillar.auth.service.impl.AuthServiceImpl;
import com.sunny.datapillar.auth.service.support.UserAccessReader;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private JwtUtil jwtUtil;

    @Mock
    private JwtToken jwtToken;

    @Mock
    private SessionStateStore sessionStateStore;

    @Mock
    private AuthCookieManager authCookieManager;

    @Mock
    private UserAccessReader userAccessReader;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void validateToken_shouldReturnSuccess_withoutTokenSign() {
        AuthDto.TokenRequest request = new AuthDto.TokenRequest();
        request.setToken("token");

        Claims claims = buildClaims("access", new Date(System.currentTimeMillis() + 60000),
                "1", "sunny", "sunny@datapillar.com", 10L);
        when(jwtUtil.parseToken("token")).thenReturn(claims);
        when(jwtUtil.getTokenType(claims)).thenReturn("access");
        when(jwtUtil.getSessionId(claims)).thenReturn("sid-1");
        when(jwtUtil.getTokenId(claims)).thenReturn("jti-1");
        when(sessionStateStore.isAccessTokenActive("sid-1", "jti-1")).thenReturn(true);
        when(jwtUtil.getUserId(claims)).thenReturn(1L);
        when(jwtUtil.getTenantId(claims)).thenReturn(10L);
        when(jwtUtil.getUsername(claims)).thenReturn("sunny");
        when(jwtUtil.getEmail(claims)).thenReturn("sunny@datapillar.com");

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

        when(jwtUtil.parseToken("token")).thenThrow(new UnauthorizedException("Token已过期"));

        UnauthorizedException exception =
                assertThrows(UnauthorizedException.class, () -> authService.validateToken(request));

        assertEquals("Token已过期", exception.getMessage());
    }

    private Claims buildClaims(String tokenType,
                               Date expiration,
                               String userId,
                               String username,
                               String email,
                               Long tenantId) {
        return Jwts.claims()
                .setSubject(userId)
                .setExpiration(expiration)
                .setIssuedAt(new Date())
                .add("tokenType", tokenType)
                .add("tenantId", tenantId)
                .add("username", username)
                .add("email", email)
                .build();
    }
}
