package com.sunny.datapillar.studio.module.user.controller;

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

import com.sunny.datapillar.studio.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.features.service.FeatureObjectService;
import com.sunny.datapillar.studio.module.user.service.RoleService;
import com.sunny.datapillar.studio.module.user.service.UserService;
import com.sunny.datapillar.studio.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 用户管理控制器
 *
 * @author sunny
 */
@Tag(name = "用户管理", description = "用户管理相关接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FeatureObjectService featureObjectService;
    private final RoleService roleService;

    @Operation(summary = "获取用户列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<UserDto.Response>> list() {
        List<UserDto.Response> users = userService.getUserList();
        return ApiResponse.ok(users);
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public ApiResponse<UserDto.Response> detail(@PathVariable Long id) {
        UserDto.Response user = userService.getUserById(id);
        return ApiResponse.ok(user);
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody UserDto.Create dto) {
        Long id = userService.createUser(dto);
        return ApiResponse.ok(id);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UserDto.Update dto) {
        userService.updateUser(id, dto);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取用户的角色")
    @GetMapping("/{id}/roles")
    public ApiResponse<List<RoleDto.Response>> getUserRoles(@PathVariable Long id) {
        List<RoleDto.Response> roles = roleService.getRolesByUserId(id);
        return ApiResponse.ok(roles);
    }

    @Operation(summary = "为用户分配角色")
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> assignRoles(@PathVariable Long id,
                                         @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取用户的功能对象")
    @GetMapping("/{id}/menus")
    public ApiResponse<List<FeatureObjectDto.TreeNode>> getUserFeatureObjects(
            @PathVariable Long id,
            @RequestParam(value = "location", required = false) String location) {
        List<FeatureObjectDto.TreeNode> featureObjects = featureObjectService.getFeatureObjectsByUserId(id, location);
        return ApiResponse.ok(featureObjects);
    }

    @Operation(summary = "获取用户权限")
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureObjectDto.ObjectPermission>> getUserPermissions(
            @PathVariable Long id) {
        List<FeatureObjectDto.ObjectPermission> permissions = userService.getUserPermissions(id);
        return ApiResponse.ok(permissions);
    }

    @Operation(summary = "更新用户权限")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateUserPermissions(
            @PathVariable Long id,
            @RequestBody FeatureObjectDto.AssignmentRequest request) {
        List<FeatureObjectDto.Assignment> permissions = request == null ? null : request.getPermissions();
        userService.updateUserPermissions(id, permissions);
        return ApiResponse.ok(null);
    }
}
