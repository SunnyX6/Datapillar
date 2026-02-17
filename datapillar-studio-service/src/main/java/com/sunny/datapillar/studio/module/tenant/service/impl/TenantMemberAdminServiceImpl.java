package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantMemberAdminService;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.service.RoleService;
import com.sunny.datapillar.studio.module.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户Member管理服务实现
 * 实现租户Member管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantMemberAdminServiceImpl implements TenantMemberAdminService {

    private final UserService userService;
    private final RoleService roleService;

    @Override
    public List<User> listUsers(Integer status) {
        return userService.listUsers(status);
    }

    @Override
    public void updateUser(Long userId, UserDto.Update dto) {
        userService.updateUser(userId, dto);
    }

    @Override
    public List<RoleDto.Response> getRolesByUserId(Long userId) {
        return roleService.getRolesByUserId(userId);
    }

    @Override
    public void assignRoles(Long userId, List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
    }

    @Override
    public List<FeatureObjectDto.ObjectPermission> getUserPermissions(Long userId) {
        return userService.getUserPermissions(userId);
    }

    @Override
    public void updateUserPermissions(Long userId, List<FeatureObjectDto.Assignment> permissions) {
        userService.updateUserPermissions(userId, permissions);
    }
}
