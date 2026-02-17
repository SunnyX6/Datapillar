package com.sunny.datapillar.studio.module.tenant.controller;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantMemberAdminService;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.common.response.ApiResponse;
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
 * 租户Member管理控制器
 * 负责租户Member管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "租户成员管理", description = "租户成员管理接口")
@RestController
@RequestMapping("/admin/tenants/{tenantId}")
@RequiredArgsConstructor
public class TenantMemberAdminController {

    private final TenantMemberAdminService tenantMemberAdminService;

    @Operation(summary = "获取租户成员列表")
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<UserDto.Response>> list(@PathVariable Long tenantId,
                                                    @RequestParam(required = false) Integer status) {
        List<User> users = tenantMemberAdminService.listUsers(status);
        List<UserDto.Response> data = users.stream()
                .map(user -> {
                    UserDto.Response response = new UserDto.Response();
                    BeanUtils.copyProperties(user, response);
                    return response;
                })
                .toList();
        return ApiResponse.ok(data);
    }

    @Operation(summary = "更新成员状态")
    @PatchMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateStatus(@PathVariable Long tenantId,
                                          @PathVariable Long userId,
                                          @Valid @RequestBody UserDto.StatusUpdate request) {
        UserDto.Update update = new UserDto.Update();
        if (request != null) {
            update.setStatus(request.getStatus());
        }
        tenantMemberAdminService.updateUser(userId, update);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取成员角色")
    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<RoleDto.Response>> roles(@PathVariable Long tenantId,
                                                     @PathVariable Long userId) {
        return ApiResponse.ok(tenantMemberAdminService.getRolesByUserId(userId));
    }

    @Operation(summary = "更新成员角色")
    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateRoles(@PathVariable Long tenantId,
                                         @PathVariable Long userId,
                                         @Valid @RequestBody List<Long> roleIds) {
        tenantMemberAdminService.assignRoles(userId, roleIds);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取成员权限")
    @GetMapping("/users/{userId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureObjectDto.ObjectPermission>> permissions(@PathVariable Long tenantId,
                                                                            @PathVariable Long userId) {
        return ApiResponse.ok(tenantMemberAdminService.getUserPermissions(userId));
    }

    @Operation(summary = "更新成员权限")
    @PutMapping("/users/{userId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updatePermissions(@PathVariable Long tenantId,
                                               @PathVariable Long userId,
                                               @Valid @RequestBody List<FeatureObjectDto.Assignment> permissions) {
        tenantMemberAdminService.updateUserPermissions(userId, permissions);
        return ApiResponse.ok();
    }
}
