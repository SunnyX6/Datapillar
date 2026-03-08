package com.sunny.datapillar.auth.api.session;

import com.sunny.datapillar.auth.dto.auth.response.AuthenticationContextResponse;
import com.sunny.datapillar.auth.dto.auth.response.TokenInfoResponse;
import com.sunny.datapillar.auth.dto.login.response.LoginResultResponse;
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

  @GetMapping("/context")
  public ApiResponse<AuthenticationContextResponse> context(
      HttpServletRequest request,
      @CookieValue(name = "auth-token", required = false) String accessToken) {
    String token = sessionAppService.extractAuthorizationToken(request, accessToken);
    return ApiResponse.ok(sessionAppService.context(token));
  }

  public record SimpleLoginRequest(
      @NotBlank(message = "login_alias must not be blank") String loginAlias,
      @NotBlank(message = "password must not be blank") String password,
      String tenantCode,
      Boolean rememberMe) {}
}
