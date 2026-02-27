package com.sunny.datapillar.studio.module.tenant.controller;

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
import com.sunny.datapillar.studio.module.tenant.service.TenantRoleAdminService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户角色管理控制器
 * 负责租户角色管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户角色", description = "租户角色接口")
@RestController
@RequestMapping("/admin/tenant/current/roles")
@RequiredArgsConstructor
public class TenantRoleAdminController {

    private final TenantRoleAdminService tenantRoleAdminService;

    @Operation(summary = "获取角色列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.ok(tenantRoleAdminService.getRoleList());
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> create(@Valid @RequestBody RoleCreateRequest dto) {
        tenantRoleAdminService.createRole(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新角色")
    @PatchMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long roleId,
                                    @Valid @RequestBody RoleUpdateRequest dto) {
        tenantRoleAdminService.updateRole(roleId, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long roleId) {
        tenantRoleAdminService.deleteRole(roleId);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取角色权限")
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureObjectPermissionItem>> permissions(@PathVariable Long roleId,
                                                                            @RequestParam(value = "scope", required = false) String scope) {
        return ApiResponse.ok(tenantRoleAdminService.getRolePermissions(roleId, scope));
    }

    @Operation(summary = "更新角色权限")
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updatePermissions(@PathVariable Long roleId,
                                               @Valid @RequestBody List<RoleFeatureAssignmentItem> permissions) {
        tenantRoleAdminService.updateRolePermissions(roleId, permissions);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取角色成员列表")
    @GetMapping("/{roleId}/members")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<RoleMembersResponse> members(@PathVariable Long roleId,
                                                         @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(tenantRoleAdminService.getRoleMembers(roleId, status));
    }

    @Operation(summary = "批量移除角色成员")
    @DeleteMapping("/{roleId}/members")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> removeMembers(@PathVariable Long roleId,
                                           @Valid @RequestBody RoleMemberBatchRemoveRequest request) {
        tenantRoleAdminService.removeRoleMembers(roleId, request.getUserIds());
        return ApiResponse.ok();
    }
}
