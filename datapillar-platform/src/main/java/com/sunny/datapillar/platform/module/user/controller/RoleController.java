package com.sunny.datapillar.platform.module.user.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.platform.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.platform.module.user.dto.RoleDto;
import com.sunny.datapillar.platform.module.user.service.RoleService;
import com.sunny.datapillar.platform.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 角色管理控制器
 *
 * @author sunny
 */
@Tag(name = "角色管理", description = "角色管理相关接口")
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "获取角色列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<RoleDto.Response>> list() {
        List<RoleDto.Response> roles = roleService.getRoleList();
        return ApiResponse.ok(roles);
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<RoleDto.Response> detail(@PathVariable Long id) {
        RoleDto.Response role = roleService.getRoleById(id);
        return ApiResponse.ok(role);
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody RoleDto.Create dto) {
        Long id = roleService.createRole(dto);
        return ApiResponse.ok(id);
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody RoleDto.Update dto) {
        roleService.updateRole(id, dto);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取角色权限")
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureObjectDto.ObjectPermission>> getPermissions(
            @PathVariable Long id,
            @RequestParam(value = "scope", required = false) String scope) {
        List<FeatureObjectDto.ObjectPermission> permissions = roleService.getRolePermissions(id, scope);
        return ApiResponse.ok(permissions);
    }

    @Operation(summary = "更新角色权限")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updatePermissions(
            @PathVariable Long id,
            @RequestBody FeatureObjectDto.AssignmentRequest request) {
        List<FeatureObjectDto.Assignment> permissions = request == null ? null : request.getPermissions();
        roleService.updateRolePermissions(id, permissions);
        return ApiResponse.ok(null);
    }
}
