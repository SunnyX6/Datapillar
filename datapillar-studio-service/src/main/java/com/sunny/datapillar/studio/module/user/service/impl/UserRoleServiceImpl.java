package com.sunny.datapillar.studio.module.user.service.impl;

import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.service.RoleService;
import com.sunny.datapillar.studio.module.user.service.UserRoleService;
import com.sunny.datapillar.studio.module.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户角色服务实现
 * 实现用户角色业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final RoleService roleService;
    private final UserService userService;

    @Override
    public List<RoleDto.Response> listRolesByUser(Long userId) {
        return roleService.getRolesByUserId(userId);
    }

    @Override
    public void assignRoles(Long userId, List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
    }
}
