package com.sunny.datapillar.auth.api.health;

import com.sunny.datapillar.auth.service.AuthAppService;
import com.sunny.datapillar.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Health endpoint controller. */
@RestController
public class HealthController {

  private final AuthAppService authAppService;

  public HealthController(AuthAppService authAppService) {
    this.authAppService = authAppService;
  }

  @GetMapping("/auth/health")
  public ApiResponse<java.util.Map<String, Object>> health() {
    return ApiResponse.ok(authAppService.health());
  }
}
