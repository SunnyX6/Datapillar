package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.dto.auth.request.*;
import com.sunny.datapillar.auth.dto.auth.response.*;
import com.sunny.datapillar.auth.dto.login.request.*;
import com.sunny.datapillar.auth.dto.login.response.*;
import com.sunny.datapillar.auth.dto.oauth.request.*;
import com.sunny.datapillar.auth.dto.oauth.response.*;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Auth service contract.
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface AuthService {

  /** Refresh session tokens using refresh token and re-issue auth cookies. */
  LoginResponse refreshToken(String refreshToken, HttpServletResponse response);

  /** Clear auth-related cookies. */
  void clearAuthCookies(HttpServletResponse response);

  /** Validate access-token integrity and online-session state. */
  TokenResponse validateToken(TokenRequest request);

  /** Impersonate target tenant for platform admin and switch access token. */
  LoginResponse assumeTenant(Long tenantId, String accessToken, HttpServletResponse response);

  /** Return token basics (ttl, expiry, and subject info). */
  TokenInfoResponse getTokenInfo(String accessToken);

  /** Parse access token and return auth context for gateway assertions. */
  AuthenticationContextResponse resolveAuthenticationContext(String token);

  /** Resolve API key and return auth context for gateway assertions. */
  AuthenticationContextResponse resolveApiKeyContext(
      String apiKey, String clientIp, String traceId);
}
