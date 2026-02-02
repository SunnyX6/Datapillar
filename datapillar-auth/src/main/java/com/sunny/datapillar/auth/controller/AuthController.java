package com.sunny.datapillar.auth.controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.service.AuthService;
import com.sunny.datapillar.auth.web.response.ApiResponse;

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
    /**
     * 登录
     */
    @PostMapping("/login")
    public ApiResponse<AuthDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request,
                                                    HttpServletResponse response) {
        AuthDto.LoginResponse loginResponse = authService.login(request, response);
        return ApiResponse.ok(loginResponse);
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthDto.LoginResponse> refresh(
            @CookieValue(name = "refresh-token", required = false) String refreshToken,
            HttpServletResponse response) {
        AuthDto.LoginResponse loginResponse = authService.refreshToken(refreshToken, response);
        return ApiResponse.ok(loginResponse);
    }

    /**
     * 验证 Token
     */
    @PostMapping("/validate")
    public ApiResponse<AuthDto.TokenResponse> validate(@Valid @RequestBody AuthDto.TokenRequest request) {
        AuthDto.TokenResponse tokenResponse = authService.validateToken(request);
        return ApiResponse.ok(tokenResponse);
    }

    /**
     * SSO 登录
     */
    @PostMapping("/sso/login")
    public ApiResponse<AuthDto.LoginResponse> ssoLogin(
            @Valid @RequestBody AuthDto.SsoLoginRequest request,
            HttpServletResponse response) {
        AuthDto.LoginResponse loginResponse = authService.loginWithSso(request, response);
        return ApiResponse.ok(loginResponse);
    }

    /**
     * SSO 扫码配置
     */
    @GetMapping("/sso/qr")
    public ApiResponse<AuthDto.SsoQrResponse> ssoQr(@RequestParam String tenantCode,
                                                    @RequestParam String provider) {
        AuthDto.SsoQrResponse qrResponse = authService.getSsoQr(tenantCode, provider);
        return ApiResponse.ok(qrResponse);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public ApiResponse<String> logout(@CookieValue(name = "auth-token", required = false) String accessToken,
            HttpServletResponse response) {
        authService.logout(accessToken, response);
        return ApiResponse.ok("登出成功");
    }

    /**
     * 获取 Token 信息
     */
    @GetMapping("/token-info")
    public ApiResponse<AuthDto.TokenInfo> getTokenInfo(
            @CookieValue(name = "auth-token", required = false) String accessToken) {
        AuthDto.TokenInfo tokenInfo = authService.getTokenInfo(accessToken);
        return ApiResponse.ok(tokenInfo);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("OK");
    }
}
