package com.sunny.datapillar.studio.module.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.service.RoleService;
import com.sunny.datapillar.studio.module.user.service.UserService;
import com.sunny.datapillar.studio.web.response.ApiResponse;
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
 * 租户成员管理接口
 */
@Tag(name = "租户成员管理", description = "租户成员管理接口")
@RestController
@RequestMapping("/tenants/{tenantId}/users")
@RequiredArgsConstructor
public class TenantUserController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;

    private final UserService userService;
    private final RoleService roleService;

    @Operation(summary = "获取租户成员列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<UserDto.Response>> list(@PathVariable Long tenantId,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) Integer limit,
                                                    @RequestParam(required = false) Integer offset) {
        int resolvedLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
        IPage<User> page = userService.listUsers(status, resolvedLimit, resolvedOffset);
        List<UserDto.Response> data = page.getRecords().stream()
                .map(user -> {
                    UserDto.Response response = new UserDto.Response();
                    BeanUtils.copyProperties(user, response);
                    return response;
                })
                .toList();
        return ApiResponse.page(data, resolvedLimit, resolvedOffset, page.getTotal());
    }

    @Operation(summary = "更新成员状态")
    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateStatus(@PathVariable Long tenantId,
                                          @PathVariable Long userId,
                                          @Valid @RequestBody UserDto.StatusUpdate request) {
        UserDto.Update update = new UserDto.Update();
        if (request != null) {
            update.setStatus(request.getStatus());
        }
        userService.updateUser(userId, update);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取成员角色")
    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<RoleDto.Response>> roles(@PathVariable Long tenantId,
                                                     @PathVariable Long userId) {
        return ApiResponse.ok(roleService.getRolesByUserId(userId));
    }

    @Operation(summary = "更新成员角色")
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updateRoles(@PathVariable Long tenantId,
                                         @PathVariable Long userId,
                                         @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取成员权限")
    @GetMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<FeatureObjectDto.ObjectPermission>> permissions(@PathVariable Long tenantId,
                                                                            @PathVariable Long userId) {
        return ApiResponse.ok(userService.getUserPermissions(userId));
    }

    @Operation(summary = "更新成员权限")
    @PutMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> updatePermissions(@PathVariable Long tenantId,
                                               @PathVariable Long userId,
                                               @RequestBody List<FeatureObjectDto.Assignment> permissions) {
        userService.updateUserPermissions(userId, permissions);
        return ApiResponse.ok(null);
    }
}
