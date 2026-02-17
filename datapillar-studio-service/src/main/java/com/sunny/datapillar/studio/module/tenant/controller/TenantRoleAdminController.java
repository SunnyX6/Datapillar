package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户角色管理控制器
 * 负责租户角色管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户角色管理", description = "租户角色管理接口")
@RestController
@RequestMapping("/admin/tenants/{tenantId}")
@RequiredArgsConstructor
public class TenantRoleAdminController {

    private final TenantRoleAdminService tenantRoleAdminService;

    @Operation(summary = "获取角色列表")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<RoleDto.Response>> list(@PathVariable Long tenantId) {
        return ApiResponse.ok(tenantRoleAdminService.getRoleList());
    }

    @Operation(summary = "创建角色")
    @PostMapping("/role")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> create(@PathVariable Long tenantId,
                                    @Valid @RequestBody RoleDto.Create dto) {
        tenantRoleAdminService.createRole(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新角色")
    @PatchMapping("/role/{roleId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @PathVariable Long roleId,
                                    @Valid @RequestBody RoleDto.Update dto) {
        tenantRoleAdminService.updateRole(roleId, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/role/{roleId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long tenantId,
                                    @PathVariable Long roleId) {
        tenantRoleAdminService.deleteRole(roleId);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取角色权限")
    @GetMapping("/role/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureObjectDto.ObjectPermission>> permissions(@PathVariable Long tenantId,
                                                                            @PathVariable Long roleId,
                                                                            @RequestParam(value = "scope", required = false) String scope) {
        return ApiResponse.ok(tenantRoleAdminService.getRolePermissions(roleId, scope));
    }

    @Operation(summary = "更新角色权限")
    @PutMapping("/role/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updatePermissions(@PathVariable Long tenantId,
                                               @PathVariable Long roleId,
                                               @Valid @RequestBody List<FeatureObjectDto.Assignment> permissions) {
        tenantRoleAdminService.updateRolePermissions(roleId, permissions);
        return ApiResponse.ok();
    }
}
