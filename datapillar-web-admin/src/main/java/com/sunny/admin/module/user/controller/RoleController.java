package com.sunny.admin.module.user.controller;

import com.sunny.common.response.ApiResponse;
import com.sunny.admin.module.user.dto.RoleReqDto;
import com.sunny.admin.module.user.dto.RoleRespDto;
import com.sunny.admin.module.user.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 角色管理控制器
 * 
 * @author sunny
 * @since 2024-01-01
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
    public ApiResponse<List<RoleRespDto>> getRoleList() {
        List<RoleRespDto> roles = roleService.getRoleList();
        return ApiResponse.ok(roles);
    }

    @Operation(summary = "根据ID获取角色详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<RoleRespDto> getRoleById(@PathVariable Long id) {
        RoleRespDto role = roleService.getRoleById(id);
        return ApiResponse.ok(role);
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<RoleRespDto> createRole(@Valid @RequestBody RoleReqDto request) {
        RoleRespDto role = roleService.createRole(request);
        return ApiResponse.ok(role);
    }

    @Operation(summary = "更新角色")
    @PostMapping("/update/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<RoleRespDto> updateRole(@PathVariable Long id, @Valid @RequestBody RoleReqDto request) {
        RoleRespDto role = roleService.updateRole(id, request);
        return ApiResponse.ok(role);
    }

    @Operation(summary = "删除角色")
    @PostMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "为角色分配权限")
    @PostMapping("/assign-permissions/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return ApiResponse.ok(null);
    }
}