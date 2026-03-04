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
import com.sunny.datapillar.studio.module.tenant.service.TenantInvitationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant invitation management controller Responsible for tenant invitation management interface
 * orchestration and request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "Tenant invitation", description = "Tenant invitation interface")
@RestController
@RequestMapping("/admin/tenant/current/invitations")
@RequiredArgsConstructor
public class TenantInvitationAdminController {

  private final TenantInvitationAdminService tenantInvitationAdminService;

  @Operation(summary = "Create invitation")
  @PostMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<InvitationCreateResponse> create(
      @Valid @RequestBody InvitationCreateRequest dto) {
    return ApiResponse.ok(tenantInvitationAdminService.createInvitation(dto));
  }
}
