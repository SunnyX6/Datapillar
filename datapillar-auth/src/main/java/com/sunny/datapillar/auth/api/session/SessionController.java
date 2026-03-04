package com.sunny.datapillar.auth.api.session;

import com.sunny.datapillar.auth.dto.auth.response.TokenInfoResponse;
import com.sunny.datapillar.auth.dto.login.response.LoginResultResponse;
import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.service.SessionAppService;
import com.sunny.datapillar.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Session endpoint controller. */
@RestController
@RequestMapping("/auth/session")
public class SessionController {

  private final SessionAppService sessionAppService;

  public SessionController(SessionAppService sessionAppService) {
    this.sessionAppService = sessionAppService;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResultResponse> loginSimple(
      @Valid @RequestBody SimpleLoginRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    LoginResultResponse response =
        sessionAppService.loginSimple(
            request.loginAlias(),
            request.password(),
            request.tenantCode(),
            request.rememberMe(),
            httpServletRequest,
            httpServletResponse);
    return ApiResponse.ok(response);
  }

  @PostMapping("/oauth2/login")
  public ApiResponse<LoginResultResponse> loginOauth2(
      @Valid @RequestBody OAuth2LoginRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    LoginResultResponse response =
        sessionAppService.loginOauth2(
            request.provider(),
            request.code(),
            request.state(),
            request.nonce(),
            request.codeVerifier(),
            request.tenantCode(),
            request.rememberMe(),
            httpServletRequest,
            httpServletResponse);
    return ApiResponse.ok(response);
  }

  @GetMapping("/oauth2/authorize")
  public ApiResponse<SsoQrResponse> oauth2Authorize(
      @RequestParam("provider") String provider,
      @RequestParam("tenant_code") String tenantCode,
      @RequestParam("nonce") String nonce,
      @RequestParam("code_challenge") String codeChallenge,
      @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod) {
    return ApiResponse.ok(
        sessionAppService.oauth2Authorize(
            provider, tenantCode, nonce, codeChallenge, codeChallengeMethod));
  }

  @PostMapping("/refresh")
  public ApiResponse<Void> refresh(
      @CookieValue(name = "refresh-token", required = false) String refreshToken,
      HttpServletResponse response) {
    sessionAppService.refresh(refreshToken, response);
    return ApiResponse.ok();
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(
      HttpServletRequest request,
      @CookieValue(name = "auth-token", required = false) String accessToken,
      HttpServletResponse response) {
    String token = sessionAppService.extractAuthorizationToken(request, accessToken);
    sessionAppService.logout(token, response);
    return ApiResponse.ok();
  }

  @GetMapping("/me")
  public ApiResponse<TokenInfoResponse> me(
      HttpServletRequest request,
      @CookieValue(name = "auth-token", required = false) String accessToken) {
    String token = sessionAppService.extractAuthorizationToken(request, accessToken);
    return ApiResponse.ok(sessionAppService.me(token));
  }

  public record SimpleLoginRequest(
      @NotBlank(message = "login_alias must not be blank") String loginAlias,
      @NotBlank(message = "password must not be blank") String password,
      @NotBlank(message = "tenant_code must not be blank") String tenantCode,
      Boolean rememberMe) {}

  public record OAuth2LoginRequest(
      @NotBlank(message = "provider must not be blank") String provider,
      @NotBlank(message = "code must not be blank") String code,
      @NotBlank(message = "state must not be blank") String state,
      @NotBlank(message = "nonce must not be blank") String nonce,
      @NotBlank(message = "code_verifier must not be blank") String codeVerifier,
      @NotBlank(message = "tenant_code must not be blank") String tenantCode,
      Boolean rememberMe) {}
}
