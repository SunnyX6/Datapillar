package com.sunny.datapillar.studio.module.user.controller;

import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.service.UserRoleService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户角色管理控制器
 * 负责用户角色管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "用户角色管理", description = "用户角色管理接口")
@RestController
@RequestMapping("/admin/user/{userId}/roles")
@RequiredArgsConstructor
public class UserRoleAdminController {

    private final UserRoleService userRoleService;

    @Operation(summary = "获取用户的角色")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<RoleDto.Response>> list(@PathVariable Long userId) {
        return ApiResponse.ok(userRoleService.listRolesByUser(userId));
    }

    @Operation(summary = "为用户分配角色")
    @PutMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long userId,
                                    @Valid @RequestBody List<Long> roleIds) {
        userRoleService.assignRoles(userId, roleIds);
        return ApiResponse.ok();
    }
}
