package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 认证服务
 * 提供认证业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface AuthService {

    /**
     * 使用 refresh token 刷新会话令牌并重发认证 cookie。
     */
    LoginResponse refreshToken(String refreshToken, HttpServletResponse response);

    /**
     * 清理认证相关 cookie。
     */
    void clearAuthCookies(HttpServletResponse response);

    /**
     * 校验 access token 的合法性与在线状态。
     */
    TokenResponse validateToken(TokenRequest request);

    /**
     * 平台超管代入目标租户，完成 access token 切换。
     */
    LoginResponse assumeTenant(Long tenantId, String accessToken, HttpServletResponse response);

    /**
     * 返回 token 基础信息（剩余时长、过期时间、主体信息）。
     */
    TokenInfoResponse getTokenInfo(String accessToken);

    /**
     * 解析 access token 并返回网关断言所需的认证上下文。
     */
    AuthenticationContextResponse resolveAuthenticationContext(String token);
}
