package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.authentication.AuthenticationRequest;
import com.sunny.datapillar.auth.authentication.AuthenticationResult;
import com.sunny.datapillar.auth.authentication.Authenticator;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.auth.dto.auth.response.TokenInfoResponse;
import com.sunny.datapillar.auth.dto.login.response.LoginResultResponse;
import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.service.login.LoginFinalizer;
import com.sunny.datapillar.auth.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/** Session application service. */
@Service
public class SessionAppService {

  private final Authenticator authenticator;
  private final AuthProperties authProperties;
  private final LoginFinalizer loginFinalizer;
  private final LoginService loginService;
  private final AuthService authService;
  private final AuthSecurityProperties securityProperties;

  public SessionAppService(
      Authenticator authenticator,
      AuthProperties authProperties,
      LoginFinalizer loginFinalizer,
      LoginService loginService,
      AuthService authService,
      AuthSecurityProperties securityProperties) {
    this.authenticator = authenticator;
    this.authProperties = authProperties;
    this.loginFinalizer = loginFinalizer;
    this.loginService = loginService;
    this.authService = authService;
    this.securityProperties = securityProperties;
  }

  public LoginResultResponse loginSimple(
      String loginAlias,
      String password,
      String tenantCode,
      Boolean rememberMe,
      HttpServletRequest request,
      HttpServletResponse response) {
    ensureAuthenticator("simple");
    String clientIp = ClientIpUtil.getClientIp(request, securityProperties.getTrustedProxies());
    AuthenticationRequest authenticationRequest =
        AuthenticationRequest.builder()
            .loginAlias(loginAlias)
            .password(password)
            .tenantCode(tenantCode)
            .rememberMe(rememberMe)
            .clientIp(clientIp)
            .build();
    AuthenticationResult authenticationResult = authenticator.authenticate(authenticationRequest);
    return loginFinalizer.finalize(authenticationResult.getSubject(), rememberMe, response);
  }

  public LoginResultResponse loginOauth2(
      String provider,
      String code,
      String state,
      String nonce,
      String codeVerifier,
      String tenantCode,
      Boolean rememberMe,
      HttpServletRequest request,
      HttpServletResponse response) {
    ensureAuthenticator("oauth2");
    String clientIp = ClientIpUtil.getClientIp(request, securityProperties.getTrustedProxies());
    AuthenticationRequest authenticationRequest =
        AuthenticationRequest.builder()
            .provider(provider)
            .code(code)
            .state(state)
            .nonce(nonce)
            .codeVerifier(codeVerifier)
            .tenantCode(tenantCode)
            .rememberMe(rememberMe)
            .clientIp(clientIp)
            .build();
    AuthenticationResult authenticationResult = authenticator.authenticate(authenticationRequest);
    return loginFinalizer.finalize(authenticationResult.getSubject(), rememberMe, response);
  }

  public void refresh(String refreshToken, HttpServletResponse response) {
    authService.refreshToken(refreshToken, response);
  }

  public void logout(String accessToken, HttpServletResponse response) {
    loginService.logout(accessToken, response);
  }

  public TokenInfoResponse me(String accessToken) {
    return authService.getTokenInfo(accessToken);
  }

  public SsoQrResponse oauth2Authorize(
      String provider,
      String tenantCode,
      String nonce,
      String codeChallenge,
      String codeChallengeMethod) {
    ensureAuthenticator("oauth2");
    AuthenticationRequest request =
        AuthenticationRequest.builder()
            .provider(provider)
            .tenantCode(tenantCode)
            .nonce(nonce)
            .codeChallenge(codeChallenge)
            .codeChallengeMethod(codeChallengeMethod)
            .build();
    return authenticator.authorize(request);
  }

  public String extractAccessToken(String authorization, String cookieToken) {
    if (authorization != null && authorization.startsWith("Bearer ")) {
      return authorization.substring(7);
    }
    return cookieToken;
  }

  public String extractAuthorizationToken(HttpServletRequest request, String cookieToken) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    return extractAccessToken(authorization, cookieToken);
  }

  private void ensureAuthenticator(String expected) {
    if (!expected.equalsIgnoreCase(authenticator.name())) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "auth.authenticator=%s does not support this endpoint",
          authProperties.getAuthenticator());
    }
  }
}
