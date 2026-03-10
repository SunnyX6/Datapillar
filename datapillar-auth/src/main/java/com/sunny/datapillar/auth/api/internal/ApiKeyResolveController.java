package com.sunny.datapillar.auth.api.internal;

import com.sunny.datapillar.auth.dto.auth.request.ApiKeyResolveRequest;
import com.sunny.datapillar.auth.dto.auth.response.AuthenticationContextResponse;
import com.sunny.datapillar.auth.service.AuthService;
import com.sunny.datapillar.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal controller for API key context resolution. */
@RestController
@RequestMapping("/internal/security/api-keys")
@RequiredArgsConstructor
public class ApiKeyResolveController {

  private final AuthService authService;

  @PostMapping("/resolve")
  public ApiResponse<AuthenticationContextResponse> resolve(
      @Valid @RequestBody ApiKeyResolveRequest request) {
    return ApiResponse.ok(
        authService.resolveApiKeyContext(
            request.getApiKey(), request.getClientIp(), request.getTraceId()));
  }
}
