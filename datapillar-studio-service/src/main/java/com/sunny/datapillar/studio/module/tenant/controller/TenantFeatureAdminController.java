package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import com.sunny.datapillar.studio.module.tenant.service.TenantFeatureAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant Function Management Controller Responsible for tenant function management interface
 * orchestration and request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "Tenant functions", description = "Tenant functional interface")
@RestController
@RequestMapping("/admin/tenant/current/features")
@RequiredArgsConstructor
public class TenantFeatureAdminController {

  private final TenantFeatureAdminService tenantFeatureAdminService;

  @Operation(summary = "Get the tenant function authorization list")
  @GetMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<List<TenantFeatureItem>> list() {
    return ApiResponse.ok(tenantFeatureAdminService.listEntitlements());
  }

  @Operation(summary = "Update tenant feature authorization")
  @PutMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<Void> update(@Valid @RequestBody List<TenantFeatureUpdateItem> items) {
    tenantFeatureAdminService.updateEntitlements(items);
    return ApiResponse.ok(null);
  }
}
