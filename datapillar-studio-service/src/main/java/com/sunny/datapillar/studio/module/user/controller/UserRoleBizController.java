package com.sunny.datapillar.studio.module.user.controller;

import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.service.UserRoleService;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户角色业务控制器
 * 负责用户角色业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "用户角色", description = "当前用户角色接口")
@RestController
@RequestMapping("/biz/users/me/roles")
@RequiredArgsConstructor
public class UserRoleBizController {

    private final UserRoleService userRoleService;

    @Operation(summary = "获取当前用户角色")
    @GetMapping
    public ApiResponse<List<RoleDto.Response>> listMyRoles() {
        Long userId = UserContextUtil.getRequiredUserId();
        return ApiResponse.ok(userRoleService.listRolesByUser(userId));
    }
}
