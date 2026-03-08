package com.sunny.datapillar.auth.api.session;

import com.sunny.datapillar.auth.dto.login.response.LoginResultResponse;
import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.service.SessionAppService;
import com.sunny.datapillar.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OAuth2 session endpoint controller. */
@RestController
@RequestMapping("/auth/session/oauth2")
@ConditionalOnProperty(prefix = "auth", name = "authenticator", havingValue = "oauth2")
public class OAuth2SessionController {

  private final SessionAppService sessionAppService;

  public OAuth2SessionController(SessionAppService sessionAppService) {
    this.sessionAppService = sessionAppService;
  }

  @PostMapping("/login")
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

  @GetMapping("/authorize")
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

  public record OAuth2LoginRequest(
      @NotBlank(message = "provider must not be blank") String provider,
      @NotBlank(message = "code must not be blank") String code,
      @NotBlank(message = "state must not be blank") String state,
      @NotBlank(message = "nonce must not be blank") String nonce,
      @NotBlank(message = "code_verifier must not be blank") String codeVerifier,
      String tenantCode,
      Boolean rememberMe) {}
}
