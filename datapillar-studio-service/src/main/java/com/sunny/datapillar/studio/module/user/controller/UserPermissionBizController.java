package com.sunny.datapillar.studio.module.user.controller;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.service.UserPermissionService;
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
 * 用户权限业务控制器
 * 负责用户权限业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "用户权限", description = "当前用户权限接口")
@RestController
@RequestMapping("/biz/users/me/permissions")
@RequiredArgsConstructor
public class UserPermissionBizController {

    private final UserPermissionService userPermissionService;

    @Operation(summary = "获取当前用户权限")
    @GetMapping
    public ApiResponse<List<FeatureObjectDto.ObjectPermission>> listMyPermissions() {
        Long userId = UserContextUtil.getRequiredUserId();
        return ApiResponse.ok(userPermissionService.listPermissions(userId));
    }
}
