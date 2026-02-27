package com.sunny.datapillar.auth.service.login.method.sso.provider;

import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoUserInfo;

/**
 * 单点登录提供器接口
 * 定义单点登录提供器能力契约与行为边界
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface SsoProvider {
    /**
     * 返回 provider 标识（dingtalk/wecom/feishu/lark）
     */
    String provider();

    /**
     * 构建扫码/授权配置
     */
    SsoQrResponse buildQr(SsoProviderConfig config, String state);

    /**
     * 授权码换取 token
     */
    SsoToken exchangeCode(SsoProviderConfig config, String authCode);

    /**
     * 获取用户信息
     */
    SsoUserInfo fetchUserInfo(SsoProviderConfig config, SsoToken token);

    /**
     * 提取稳定外部用户 ID
     */
    default String extractExternalUserId(SsoUserInfo userInfo) {
        return userInfo == null ? null : userInfo.getExternalUserId();
    }
}
