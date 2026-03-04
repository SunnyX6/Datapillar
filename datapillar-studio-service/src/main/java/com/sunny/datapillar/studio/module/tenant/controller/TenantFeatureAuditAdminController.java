package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.tenant.service.TenantFeatureAuditAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant functionsAuditmanagement controller Responsible for tenant functionsAuditManagement
 * interface orchestration and request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "Tenant functions", description = "Tenant functional interface")
@RestController
@RequestMapping("/admin/tenant/current/features/audits")
@RequiredArgsConstructor
public class TenantFeatureAuditAdminController {

  private final TenantFeatureAuditAdminService tenantFeatureAuditAdminService;

  @Operation(summary = "Get tenant function authorization audit")
  @GetMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<List<TenantFeatureAudit>> audit() {
    return ApiResponse.ok(tenantFeatureAuditAdminService.listAudits());
  }
}
