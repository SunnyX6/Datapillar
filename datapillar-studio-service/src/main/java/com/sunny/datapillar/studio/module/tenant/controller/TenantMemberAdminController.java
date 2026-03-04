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
import com.sunny.datapillar.studio.module.tenant.service.TenantMemberAdminService;
import com.sunny.datapillar.studio.module.user.entity.User;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * tenantMembermanagement controller Responsible for tenantsMemberManagement interface orchestration
 * and request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "Tenant member", description = "Tenant member interface")
@RestController
@RequestMapping("/admin/tenant/current/members")
@RequiredArgsConstructor
public class TenantMemberAdminController {

  private final TenantMemberAdminService tenantMemberAdminService;

  @Operation(summary = "Get the list of tenant members")
  @GetMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<List<UserResponse>> list(@RequestParam(required = false) Integer status) {
    List<User> users = tenantMemberAdminService.listUsers(status);
    List<UserResponse> data =
        users.stream()
            .map(
                user -> {
                  UserResponse response = new UserResponse();
                  BeanUtils.copyProperties(user, response);
                  return response;
                })
            .toList();
    return ApiResponse.ok(data);
  }

  @Operation(summary = "Update member status")
  @PatchMapping("/{memberId}/status")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<Void> updateStatus(
      @PathVariable Long memberId, @Valid @RequestBody UserStatusRequest request) {
    Integer status = request == null ? null : request.getStatus();
    tenantMemberAdminService.updateMemberStatus(memberId, status);
    return ApiResponse.ok();
  }

  @Operation(summary = "Get member role")
  @GetMapping("/{memberId}/roles")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<List<RoleResponse>> roles(@PathVariable Long memberId) {
    return ApiResponse.ok(tenantMemberAdminService.getRolesByUserId(memberId));
  }

  @Operation(summary = "Update member role")
  @PutMapping("/{memberId}/roles")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ApiResponse<Void> updateRoles(
      @PathVariable Long memberId, @Valid @RequestBody List<Long> roleIds) {
    tenantMemberAdminService.assignRoles(memberId, roleIds);
    return ApiResponse.ok();
  }
}
