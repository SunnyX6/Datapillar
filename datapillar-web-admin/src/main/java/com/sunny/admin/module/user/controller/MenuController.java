package com.sunny.admin.module.user.controller;

import com.sunny.common.response.ApiResponse;
import com.sunny.admin.module.user.dto.MenuRespDto;
import com.sunny.admin.module.user.dto.PermissionRespDto;
import com.sunny.admin.module.user.entity.Permission;
import com.sunny.admin.module.user.mapper.PermissionMapper;
import com.sunny.admin.module.user.service.MenuService;
import com.sunny.admin.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单和权限管理控制器
 *
 * @author sunny
 * @since 2024-01-01
 */
@Tag(name = "菜单管理", description = "菜单和权限管理相关接口")
@RestController
@RequestMapping("/menus")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private SecurityUtil securityUtil;

    @Operation(summary = "获取当前用户的菜单列表", description = "获取当前登录用户有权访问的菜单列表")
    @GetMapping("/me")
    public ApiResponse<List<MenuRespDto>> getCurrentUserMenus() {
        Long currentUserId = securityUtil.getCurrentUserId();
        List<MenuRespDto> menus = menuService.getMenusByUserId(currentUserId);
        return ApiResponse.ok(menus);
    }

    @Operation(summary = "获取所有可见菜单", description = "获取系统中所有可见状态的菜单（仅管理员）")
    @GetMapping("/visible")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<MenuRespDto>> getVisibleMenus() {
        List<MenuRespDto> menus = menuService.getAllVisibleMenus();
        return ApiResponse.ok(menus);
    }

    @Operation(summary = "获取菜单树结构", description = "获取系统菜单的树形结构，包含父子关系（仅管理员）")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<MenuRespDto>> getMenuTree() {
        List<MenuRespDto> allMenus = menuService.getAllVisibleMenus();
        List<MenuRespDto> menuTree = menuService.buildMenuTree(allMenus);
        return ApiResponse.ok(menuTree);
    }

    @Operation(summary = "获取所有权限列表", description = "获取系统中所有菜单权限的列表（仅管理员）")
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<List<PermissionRespDto>> getAllPermissions() {
        List<Permission> permissions = permissionMapper.selectList(null);
        List<PermissionRespDto> responses = permissions.stream()
                .map(this::convertToPermissionResponse)
                .collect(Collectors.toList());
        return ApiResponse.ok(responses);
    }

    @Operation(summary = "根据ID获取权限详情", description = "根据权限ID获取权限的详细信息（仅管理员）")
    @GetMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse<PermissionRespDto> getPermissionById(@PathVariable Long id) {
        Permission permission = permissionMapper.selectById(id);
        if (permission == null) {
            return ApiResponse.error("PERMISSION_NOT_FOUND", "权限不存在");
        }
        return ApiResponse.ok(convertToPermissionResponse(permission));
    }

    /**
     * 转换Permission实体为响应DTO
     */
    private PermissionRespDto convertToPermissionResponse(Permission permission) {
        PermissionRespDto response = new PermissionRespDto();
        response.setId(permission.getId());
        response.setCode(permission.getCode());
        response.setName(permission.getName());
        response.setDescription(permission.getDescription());
        response.setCreatedAt(permission.getCreatedAt());
        response.setUpdatedAt(permission.getUpdatedAt());
        return response;
    }
}