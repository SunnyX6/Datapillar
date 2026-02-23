package com.sunny.datapillar.studio.module.user.controller;

import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.service.UserAdminService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理控制器
 * 负责用户管理接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "用户", description = "用户接口")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "获取用户列表")
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<UserDto.Response>> list() {
        return ApiResponse.ok(userAdminService.listUsers());
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<UserDto.Response> detail(@PathVariable Long id) {
        return ApiResponse.ok(userAdminService.getUser(id));
    }

    @Operation(summary = "创建用户")
    @PostMapping("/user")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> create(@Valid @RequestBody UserDto.Create dto) {
        userAdminService.createUser(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新用户")
    @PutMapping("/user/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UserDto.Update dto) {
        userAdminService.updateUser(id, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/user/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userAdminService.deleteUser(id);
        return ApiResponse.ok();
    }
}
