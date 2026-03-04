package com.sunny.datapillar.auth.service.login.method.sso.provider;

import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoUserInfo;

/**
 * Contract for SSO providers.
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface SsoProvider {
  /** Return provider identifier (dingtalk/wecom/feishu/lark). */
  String provider();

  /** Build QR/authorization config. */
  SsoQrResponse buildQr(SsoProviderConfig config, String state);

  /** Exchange authorization code for token. */
  SsoToken exchangeCode(SsoProviderConfig config, String authCode);

  /** Fetch user info from provider. */
  SsoUserInfo fetchUserInfo(SsoProviderConfig config, SsoToken token);

  /** Extract stable external user ID. */
  default String extractExternalUserId(SsoUserInfo userInfo) {
    return userInfo == null ? null : userInfo.getExternalUserId();
  }
}
