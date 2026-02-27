package com.sunny.datapillar.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 认证控制器
 * 负责认证接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证会话", description = "认证会话管理接口")
public class AuthController {

    private final AuthService authService;

    /**
     * 刷新 Token
     */
    @Operation(summary = "刷新认证会话")
    @PostMapping("/refresh")
    public ApiResponse<Void> refresh(
            @CookieValue(name = "refresh-token", required = false) String refreshToken,
            HttpServletResponse response) {
        authService.refreshToken(refreshToken, response);
        return ApiResponse.ok();
    }

    /**
     * 平台超管授权访问目标租户（assume），签发用于目标租户业务接口访问的 access token。
     */
    @Operation(summary = "代入目标租户并签发访问令牌")
    @PostMapping("/tenants/{tenantId}/assume")
    public ApiResponse<Void> assumeTenant(
            @PathVariable Long tenantId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @CookieValue(name = "auth-token", required = false) String accessToken,
            HttpServletResponse response) {
        String token = extractAccessToken(authorization, accessToken);
        authService.assumeTenant(tenantId, token, response);
        return ApiResponse.ok();
    }

    /**
     * 获取 Token 信息
     */
    @Operation(summary = "校验当前会话并返回会话信息")
    @GetMapping("/validate")
    public ApiResponse<TokenInfoResponse> validate(
            @CookieValue(name = "auth-token", required = false) String accessToken) {
        TokenInfoResponse tokenInfo = authService.getTokenInfo(accessToken);
        return ApiResponse.ok(tokenInfo);
    }

    /**
     * 健康检查
     */
    @Operation(summary = "认证服务健康检查")
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("OK");
    }

    private String extractAccessToken(String authorization, String cookieToken) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return cookieToken;
    }
}
