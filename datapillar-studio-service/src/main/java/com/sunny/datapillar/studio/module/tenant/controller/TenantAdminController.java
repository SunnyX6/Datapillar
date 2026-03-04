package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.common.exception.BadRequestException;
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
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.service.TenantAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant Management Controller Responsible for tenant management interface orchestration and
 * request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "tenant", description = "Tenant interface")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class TenantAdminController {

  private final TenantAdminService tenantAdminService;

  @Operation(summary = "Get tenant list")
  @GetMapping("/tenants")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<List<TenantResponse>> list(@RequestParam(required = false) Integer status) {
    List<Tenant> tenants = tenantAdminService.listTenants(status);
    List<TenantResponse> data =
        tenants.stream()
            .map(
                tenant -> {
                  TenantResponse response = new TenantResponse();
                  BeanUtils.copyProperties(tenant, response);
                  return response;
                })
            .toList();
    return ApiResponse.ok(data);
  }

  @Operation(summary = "Create tenant")
  @PostMapping("/tenant")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<Void> create(@Valid @RequestBody TenantCreateRequest dto) {
    tenantAdminService.createTenant(dto);
    return ApiResponse.ok();
  }

  @Operation(summary = "Get tenant details")
  @GetMapping("/tenants/{tenantId}")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<TenantResponse> detail(@PathVariable String tenantId) {
    return ApiResponse.ok(tenantAdminService.getTenant(parseTenantId(tenantId)));
  }

  @Operation(summary = "Update tenant information")
  @PatchMapping("/tenant/{tenantId}")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<Void> update(
      @PathVariable String tenantId, @Valid @RequestBody TenantUpdateRequest dto) {
    tenantAdminService.updateTenant(parseTenantId(tenantId), dto);
    return ApiResponse.ok();
  }

  @Operation(summary = "Update tenant status")
  @PatchMapping("/tenant/{tenantId}/status")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<Void> updateStatus(
      @PathVariable String tenantId, @RequestBody TenantStatusRequest dto) {
    Integer status = dto == null ? null : dto.getStatus();
    tenantAdminService.updateStatus(parseTenantId(tenantId), status);
    return ApiResponse.ok();
  }

  private Long parseTenantId(String rawTenantId) {
    if (rawTenantId == null || rawTenantId.isBlank()) {
      throw new BadRequestException("tenantId cannot be empty");
    }
    try {
      Long tenantId = Long.parseLong(rawTenantId.trim());
      if (tenantId <= 0) {
        throw new BadRequestException("tenantId Must be a positive integer");
      }
      return tenantId;
    } catch (NumberFormatException ex) {
      throw new BadRequestException(ex, "tenantId illegal");
    }
  }
}
