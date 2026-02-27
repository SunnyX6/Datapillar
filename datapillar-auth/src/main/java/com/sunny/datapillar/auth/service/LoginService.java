package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
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
    LoginResultResponse login(LoginCommand command, String clientIp, HttpServletResponse response);

    /**
     * 多租户场景下，根据 loginToken 选择租户并完成登录。
     */
    LoginResultResponse loginWithTenant(String loginToken, Long tenantId, HttpServletResponse response);

    /**
     * 获取 SSO 扫码登录配置。
     */
    SsoQrResponse getSsoQr(String tenantCode, String provider);

    /**
     * 登出并撤销会话。
     */
    void logout(String accessToken, HttpServletResponse response);
}
