package com.sunny.datapillar.auth.sso;

import com.sunny.datapillar.auth.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.sso.model.SsoQrResponse;
import com.sunny.datapillar.auth.sso.model.SsoToken;
import com.sunny.datapillar.auth.sso.model.SsoUserInfo;

/**
 * SSO Provider 抽象
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
}
