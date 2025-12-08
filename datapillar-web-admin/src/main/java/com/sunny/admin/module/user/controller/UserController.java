package com.sunny.admin.module.user.controller;

import com.sunny.common.response.ApiResponse;
import com.sunny.admin.module.user.dto.UserReqDto;
import com.sunny.admin.module.user.dto.UserRespDto;
import com.sunny.admin.module.user.dto.UpdateProfileReqDto;
import com.sunny.admin.module.user.service.UserService;
import com.sunny.admin.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 用户管理控制器
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Tag(name = "用户管理", description = "用户管理相关接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    private final SecurityUtil securityUtil;
    
    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public ApiResponse<UserRespDto> getCurrentUser() {
        Long currentUserId = securityUtil.getCurrentUserId();
        UserRespDto user = userService.getUserById(currentUserId);
        return ApiResponse.ok(user);
    }

    @Operation(summary = "获取用户列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<UserRespDto>> getUserList() {
        List<UserRespDto> users = userService.getUserList();
        return ApiResponse.ok(users);
    }

    @Operation(summary = "根据ID获取用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<UserRespDto> getUserById(@PathVariable Long id) {
        UserRespDto user = userService.getUserById(id);
        return ApiResponse.ok(user);
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<UserRespDto> createUser(@Valid @RequestBody UserReqDto request) {
        UserRespDto user = userService.createUser(request);
        return ApiResponse.ok(user);
    }

    @Operation(summary = "更新用户")
    @PostMapping("/update/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<UserRespDto> updateUser(@PathVariable Long id, @Valid @RequestBody UserReqDto request) {
        UserRespDto user = userService.updateUser(id, request);
        return ApiResponse.ok(user);
    }

    @Operation(summary = "删除用户")
    @PostMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "为用户分配角色")
    @PostMapping("/assign-roles/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "更新当前用户个人信息")
    @PostMapping("/update-profile")
    public ApiResponse<UserRespDto> updateProfile(@Valid @RequestBody UpdateProfileReqDto request) {
        Long currentUserId = securityUtil.getCurrentUserId();
        UserRespDto user = userService.updateProfile(currentUserId, request);
        return ApiResponse.ok(user);
    }
}