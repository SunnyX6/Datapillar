package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.security.JwtTokenUtil;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

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

        Claims claims = buildClaims("access", new Date(System.currentTimeMillis() + 60000), "1", "sunny", "sunny@datapillar.com");
        when(jwtTokenUtil.parseToken("token")).thenReturn(claims);

        User user = new User();
        user.setId(1L);
        user.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        AuthDto.TokenResponse response = authService.validateToken(request);

        assertTrue(response.isValid());
        assertEquals(1L, response.getUserId());
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
    void validateSsoToken_shouldThrowWhenRevoked() {
        AuthDto.SsoValidateRequest request = new AuthDto.SsoValidateRequest();
        request.setToken("sso-token");

        Claims claims = buildClaims("access", new Date(System.currentTimeMillis() + 60000), "2", "sunny", "sunny@datapillar.com");
        when(jwtTokenUtil.parseToken("sso-token")).thenReturn(claims);
        when(jwtTokenUtil.extractTokenSignature("sso-token")).thenReturn("sig");
        when(userMapper.selectByIdAndTokenSign(2L, "sig")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.validateSsoToken(request));

        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, exception.getErrorCode());
    }

    @Test
    void getTokenInfo_shouldThrowWhenBlankToken() {
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.getTokenInfo(""));

        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, exception.getErrorCode());
    }

    private Claims buildClaims(String tokenType, Date expiration, String subject, String username, String email) {
        return Jwts.claims()
                .setSubject(subject)
                .setExpiration(expiration)
                .add("tokenType", tokenType)
                .add("username", username)
                .add("email", email)
                .build();
    }
}
