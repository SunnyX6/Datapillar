package com.sunny.datapillar.auth.controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.response.AuthErrorCode;
import com.sunny.datapillar.auth.response.AuthException;
import com.sunny.datapillar.auth.response.AuthResponse;
import com.sunny.datapillar.auth.security.JwtTokenUtil;
import com.sunny.datapillar.auth.service.AuthService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 认证控制器
 *
 * @author sunny
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * 登录
     */
    @PostMapping("/login")
    public AuthResponse<AuthDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request, HttpServletResponse response) {
        try {
            AuthDto.LoginResponse loginResponse = authService.login(request, response);
            return AuthResponse.success(loginResponse);
        } catch (AuthException e) {
            return AuthResponse.error(e.getErrorCode());
        } catch (Exception e) {
            return AuthResponse.error(AuthErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public AuthResponse<AuthDto.LoginResponse> refresh(
            @CookieValue(name = "refresh-token", required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            AuthDto.LoginResponse loginResponse = authService.refreshToken(refreshToken, response);
            return AuthResponse.success(loginResponse);
        } catch (Exception e) {
            return AuthResponse.error("REFRESH_TOKEN_EXPIRED", e.getMessage());
        }
    }

    /**
     * 验证 Token
     */
    @PostMapping("/validate")
    public AuthResponse<AuthDto.TokenResponse> validate(@Valid @RequestBody AuthDto.TokenRequest request) {
        AuthDto.TokenResponse tokenResponse = authService.validateToken(request);
        return AuthResponse.success(tokenResponse);
    }

    /**
     * SSO Token 验证
     */
    @PostMapping("/sso/validate")
    public AuthResponse<AuthDto.SsoValidateResponse> validateSsoToken(@Valid @RequestBody AuthDto.SsoValidateRequest request) {
        AuthDto.SsoValidateResponse ssoResponse = authService.validateSsoToken(request);
        if (ssoResponse.getValid()) {
            return AuthResponse.success(ssoResponse);
        } else {
            return AuthResponse.error("SSO_VALIDATION_FAILED", ssoResponse.getMessage());
        }
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public AuthResponse<String> logout(@CookieValue(name = "auth-token", required = false) String accessToken,
            HttpServletResponse response) {
        try {
            if (accessToken != null && !accessToken.isEmpty()) {
                Long userId = jwtTokenUtil.getUserId(accessToken);
                authService.logout(userId);
            }
        } catch (Exception e) {
            // 即使撤销失败也继续清除 Cookie
        }

        authService.clearAuthCookies(response);
        return AuthResponse.success("登出成功");
    }

    /**
     * 获取 Token 信息
     */
    @GetMapping("/token-info")
    public AuthResponse<AuthDto.TokenInfo> getTokenInfo(
            @CookieValue(name = "auth-token", required = false) String accessToken) {
        try {
            if (accessToken == null || accessToken.isEmpty()) {
                return AuthResponse.success(AuthDto.TokenInfo.builder()
                        .valid(false)
                        .remainingSeconds(0L)
                        .build());
            }

            Claims claims = jwtTokenUtil.parseToken(accessToken);

            long expirationTime = claims.getExpiration().getTime();
            long now = System.currentTimeMillis();
            long remainingSeconds = Math.max(0, (expirationTime - now) / 1000);

            AuthDto.TokenInfo tokenInfo = AuthDto.TokenInfo.builder()
                    .valid(remainingSeconds > 0)
                    .remainingSeconds(remainingSeconds)
                    .expirationTime(expirationTime)
                    .issuedAt(claims.getIssuedAt().getTime())
                    .userId(Long.parseLong(claims.getSubject()))
                    .username(claims.get("username", String.class))
                    .build();

            return AuthResponse.success(tokenInfo);
        } catch (Exception e) {
            return AuthResponse.success(AuthDto.TokenInfo.builder()
                    .valid(false)
                    .remainingSeconds(0L)
                    .build());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public AuthResponse<String> health() {
        return AuthResponse.success("OK");
    }
}
