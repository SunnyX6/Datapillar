package com.sunny.datapillar.studio.module.user.service.impl;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.service.UserPermissionService;
import com.sunny.datapillar.studio.module.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户权限服务实现
 * 实现用户权限业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class UserPermissionServiceImpl implements UserPermissionService {

    private final UserService userService;

    @Override
    public List<FeatureObjectDto.ObjectPermission> listPermissions(Long userId) {
        return userService.getUserPermissions(userId);
    }

    @Override
    public void updatePermissions(Long userId, List<FeatureObjectDto.Assignment> permissions) {
        userService.updateUserPermissions(userId, permissions);
    }
}
