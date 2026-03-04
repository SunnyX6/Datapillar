package com.sunny.datapillar.studio.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * health check controller Responsible for health check interface orchestration and request
 * processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "health check", description = "Service health status check interface")
@RestController
public class HealthController {

  @Operation(summary = "health check")
  @GetMapping("/health")
  public ApiResponse<String> health() {
    return ApiResponse.ok("OK");
  }
}
