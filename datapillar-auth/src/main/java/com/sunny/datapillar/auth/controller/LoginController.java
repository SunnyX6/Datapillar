package com.sunny.datapillar.auth.controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.auth.service.LoginService;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.util.ClientIpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Locale;
import org.springframework.util.StringUtils;
import com.sunny.datapillar.common.exception.BadRequestException;

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
    public ApiResponse<AuthDto.LoginResult> login(@Valid @RequestBody AuthDto.LoginFlowRequest request,
                                                  @CookieValue(name = "login-token", required = false) String loginToken,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse response) {
        String clientIp = ClientIpUtil.getClientIp(httpRequest, securityProperties.getTrustedProxies());
        String stage = normalizeStage(request == null ? null : request.getStage());
        if (STAGE_AUTH.equals(stage)) {
            LoginCommand command = buildPasswordLoginCommand(request, clientIp);
            AuthDto.LoginResult loginResponse = loginService.login(command, clientIp, response);
            return ApiResponse.ok(loginResponse);
        }
        if (STAGE_TENANT_SELECT.equals(stage)) {
            Long tenantId = request == null ? null : request.getTenantId();
            AuthDto.LoginResult loginResponse = loginService.loginWithTenant(loginToken, tenantId, response);
            return ApiResponse.ok(loginResponse);
        }
        throw new BadRequestException("参数错误");
    }

    /**
     * SSO 扫码登录
     */
    @Operation(summary = "SSO扫码登录与租户选择")
    @PostMapping("/sso")
    public ApiResponse<AuthDto.LoginResult> sso(@Valid @RequestBody AuthDto.LoginFlowRequest request,
                                                @CookieValue(name = "login-token", required = false) String loginToken,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse response) {
        String clientIp = ClientIpUtil.getClientIp(httpRequest, securityProperties.getTrustedProxies());
        String stage = normalizeStage(request == null ? null : request.getStage());
        if (STAGE_AUTH.equals(stage)) {
            LoginCommand command = buildSsoLoginCommand(request, clientIp);
            AuthDto.LoginResult loginResponse = loginService.login(command, clientIp, response);
            return ApiResponse.ok(loginResponse);
        }
        if (STAGE_TENANT_SELECT.equals(stage)) {
            Long tenantId = request == null ? null : request.getTenantId();
            AuthDto.LoginResult loginResponse = loginService.loginWithTenant(loginToken, tenantId, response);
            return ApiResponse.ok(loginResponse);
        }
        throw new BadRequestException("参数错误");
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

    private LoginCommand buildPasswordLoginCommand(AuthDto.LoginFlowRequest request, String clientIp) {
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        requireText(request.getLoginAlias());
        requireText(request.getPassword());
        LoginCommand command = new LoginCommand();
        command.setMethod("password");
        command.setRememberMe(request.getRememberMe());
        command.setLoginAlias(request.getLoginAlias());
        command.setPassword(request.getPassword());
        command.setTenantCode(request.getTenantCode());
        command.setClientIp(clientIp);
        return command;
    }

    private LoginCommand buildSsoLoginCommand(AuthDto.LoginFlowRequest request, String clientIp) {
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        if (StringUtils.hasText(request.getLoginAlias()) || StringUtils.hasText(request.getPassword())) {
            throw new BadRequestException("参数错误");
        }
        requireText(request.getProvider());
        requireText(request.getCode());
        requireText(request.getState());
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

    private void requireText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("参数错误");
        }
    }
}
