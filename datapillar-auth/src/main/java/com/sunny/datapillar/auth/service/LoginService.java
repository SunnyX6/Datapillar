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
 * Login service contract.
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface LoginService {

  /** Unified login entrypoint. */
  LoginResultResponse login(LoginCommand command, String clientIp, HttpServletResponse response);

  /** Complete login by selecting tenant from loginToken in multi-tenant flows. */
  LoginResultResponse loginWithTenant(
      String loginToken, Long tenantId, HttpServletResponse response);

  /** Get SSO QR-code login configuration. */
  SsoQrResponse getSsoQr(String tenantCode, String provider);

  /** Logout and revoke the session. */
  void logout(String accessToken, HttpServletResponse response);
}
