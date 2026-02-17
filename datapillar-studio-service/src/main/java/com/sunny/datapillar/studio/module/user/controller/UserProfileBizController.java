package com.sunny.datapillar.studio.module.user.controller;

import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.service.UserProfileBizService;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户Profile业务控制器
 * 负责用户Profile业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "用户资料", description = "当前用户资料接口")
@RestController
@RequestMapping("/biz/users/me/profile")
@RequiredArgsConstructor
public class UserProfileBizController {

    private final UserProfileBizService userProfileBizService;

    @Operation(summary = "获取当前用户资料")
    @GetMapping
    public ApiResponse<UserDto.Response> profile() {
        Long userId = UserContextUtil.getRequiredUserId();
        return ApiResponse.ok(userProfileBizService.getProfile(userId));
    }

    @Operation(summary = "更新当前用户资料")
    @PatchMapping
    public ApiResponse<Void> updateProfile(@Valid @RequestBody UserDto.UpdateProfile request) {
        Long userId = UserContextUtil.getRequiredUserId();
        userProfileBizService.updateProfile(userId, request);
        return ApiResponse.ok(null);
    }
}
