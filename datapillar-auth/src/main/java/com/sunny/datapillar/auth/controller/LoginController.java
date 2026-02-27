package com.sunny.datapillar.auth.controller;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.auth.dto.login.request.LoginRequest;
import com.sunny.datapillar.auth.dto.login.response.LoginResultResponse;
import com.sunny.datapillar.auth.service.LoginService;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.util.ClientIpUtil;
import com.sunny.datapillar.auth.validation.ValidLoginRequest;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录控制器
 * 负责登录接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@RestController
@RequestMapping("/login")
@RequiredArgsConstructor
@Tag(name = "登录认证", description = "登录与登出接口")
public class LoginController {

    private static final String STAGE_AUTH = "AUTH";
    private static final String STAGE_TENANT_SELECT = "TENANT_SELECT";

    private final LoginService loginService;
    private final AuthSecurityProperties securityProperties;

    /**
     * 登录
     */
    @Operation(summary = "账号密码登录与租户选择")
    @PostMapping
    public ApiResponse<LoginResultResponse> login(
            @Valid
            @ValidLoginRequest(mode = ValidLoginRequest.LoginMode.PASSWORD)
            @RequestBody LoginRequest request,
            @CookieValue(name = "login-token", required = false) String loginToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String clientIp = ClientIpUtil.getClientIp(httpRequest, securityProperties.getTrustedProxies());
        String stage = normalizeStage(request.getStage());
        if (STAGE_TENANT_SELECT.equals(stage)) {
            LoginResultResponse loginResponse = loginService.loginWithTenant(loginToken, request.getTenantId(), response);
            return ApiResponse.ok(loginResponse);
        }

        LoginCommand command = buildPasswordLoginCommand(request, clientIp);
        LoginResultResponse loginResponse = loginService.login(command, clientIp, response);
        return ApiResponse.ok(loginResponse);
    }

    /**
     * SSO 扫码登录
     */
    @Operation(summary = "SSO扫码登录与租户选择")
    @PostMapping("/sso")
    public ApiResponse<LoginResultResponse> sso(
            @Valid
            @ValidLoginRequest(mode = ValidLoginRequest.LoginMode.SSO)
            @RequestBody LoginRequest request,
            @CookieValue(name = "login-token", required = false) String loginToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String clientIp = ClientIpUtil.getClientIp(httpRequest, securityProperties.getTrustedProxies());
        String stage = normalizeStage(request.getStage());
        if (STAGE_TENANT_SELECT.equals(stage)) {
            LoginResultResponse loginResponse = loginService.loginWithTenant(loginToken, request.getTenantId(), response);
            return ApiResponse.ok(loginResponse);
        }

        LoginCommand command = buildSsoLoginCommand(request, clientIp);
        LoginResultResponse loginResponse = loginService.login(command, clientIp, response);
        return ApiResponse.ok(loginResponse);
    }

    /**
     * 登出
     */
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<String> logout(@CookieValue(name = "auth-token", required = false) String accessToken,
                                      HttpServletResponse response) {
        loginService.logout(accessToken, response);
        return ApiResponse.ok("登出成功");
    }

    private LoginCommand buildPasswordLoginCommand(LoginRequest request, String clientIp) {
        LoginCommand command = new LoginCommand();
        command.setMethod("password");
        command.setRememberMe(request.getRememberMe());
        command.setLoginAlias(request.getLoginAlias());
        command.setPassword(request.getPassword());
        command.setTenantCode(request.getTenantCode());
        command.setClientIp(clientIp);
        return command;
    }

    private LoginCommand buildSsoLoginCommand(LoginRequest request, String clientIp) {
        LoginCommand command = new LoginCommand();
        command.setMethod("sso");
        command.setRememberMe(request.getRememberMe());
        command.setProvider(request.getProvider());
        command.setCode(request.getCode());
        command.setState(request.getState());
        command.setTenantCode(request.getTenantCode());
        command.setClientIp(clientIp);
        return command;
    }

    private String normalizeStage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
