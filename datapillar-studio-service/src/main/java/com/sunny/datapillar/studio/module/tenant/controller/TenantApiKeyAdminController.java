package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.dto.tenant.request.TenantApiKeyCreateRequest;
import com.sunny.datapillar.studio.dto.tenant.response.TenantApiKeyCreateResponse;
import com.sunny.datapillar.studio.dto.tenant.response.TenantApiKeyItemResponse;
import com.sunny.datapillar.studio.module.tenant.service.TenantApiKeyAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant API key management controller. */
@Tag(name = "Tenant API key", description = "Tenant API key interface")
@RestController
@RequestMapping("/admin/tenant/current/api-keys")
@RequiredArgsConstructor
public class TenantApiKeyAdminController {

  private final TenantApiKeyAdminService tenantApiKeyAdminService;

  @Operation(summary = "Get tenant API key list")
  @GetMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<List<TenantApiKeyItemResponse>> list() {
    return ApiResponse.ok(tenantApiKeyAdminService.listApiKeys());
  }

  @Operation(summary = "Create tenant API key")
  @PostMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<TenantApiKeyCreateResponse> create(
      @Valid @RequestBody TenantApiKeyCreateRequest request) {
    return ApiResponse.ok(tenantApiKeyAdminService.createApiKey(request));
  }

  @Operation(summary = "Disable tenant API key")
  @PostMapping("/{apiKeyId}/disable")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<Void> disable(@PathVariable Long apiKeyId) {
    tenantApiKeyAdminService.disableApiKey(apiKeyId);
    return ApiResponse.ok();
  }
}
