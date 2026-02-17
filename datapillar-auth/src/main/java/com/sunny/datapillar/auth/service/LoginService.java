package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.dto.AuthDto;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录服务
 * 提供登录业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface LoginService {

    /**
     * 统一登录入口。
     */
    AuthDto.LoginResult login(LoginCommand command, String clientIp, HttpServletResponse response);

    /**
     * 多租户场景下，根据 loginToken 选择租户并完成登录。
     */
    AuthDto.LoginResult loginWithTenant(String loginToken, Long tenantId, HttpServletResponse response);

    /**
     * 获取 SSO 扫码登录配置。
     */
    AuthDto.SsoQrResponse getSsoQr(String tenantCode, String provider);

    /**
     * 登出并撤销会话。
     */
    void logout(String accessToken, HttpServletResponse response);
}
