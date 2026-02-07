package com.sunny.datapillar.studio.module.user.controller;

import com.sunny.datapillar.studio.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.service.RoleService;
import com.sunny.datapillar.studio.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
 * 租户角色管理接口
 */
@Tag(name = "租户角色管理", description = "租户角色管理接口")
@RestController
@RequestMapping("/tenants/{tenantId}/roles")
@RequiredArgsConstructor
public class TenantRoleController {

    private final RoleService roleService;

    @Operation(summary = "获取角色列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<RoleDto.Response>> list(@PathVariable Long tenantId) {
        return ApiResponse.ok(roleService.getRoleList());
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Map<String, Long>> create(@PathVariable Long tenantId,
                                                 @Valid @RequestBody RoleDto.Create dto) {
        Long id = roleService.createRole(dto);
        return ApiResponse.ok(Map.of("roleId", id));
    }

    @Operation(summary = "更新角色")
    @PatchMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long tenantId,
                                    @PathVariable Long roleId,
                                    @Valid @RequestBody RoleDto.Update dto) {
        roleService.updateRole(roleId, dto);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long tenantId,
                                    @PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取角色权限")
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureObjectDto.ObjectPermission>> permissions(@PathVariable Long tenantId,
                                                                            @PathVariable Long roleId,
                                                                            @RequestParam(value = "scope", required = false) String scope) {
        return ApiResponse.ok(roleService.getRolePermissions(roleId, scope));
    }

    @Operation(summary = "更新角色权限")
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updatePermissions(@PathVariable Long tenantId,
                                               @PathVariable Long roleId,
                                               @RequestBody List<FeatureObjectDto.Assignment> permissions) {
        roleService.updateRolePermissions(roleId, permissions);
        return ApiResponse.ok(null);
    }
}
