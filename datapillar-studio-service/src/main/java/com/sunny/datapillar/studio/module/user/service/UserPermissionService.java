package com.sunny.datapillar.studio.module.user.service;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import java.util.List;

/**
 * 用户权限服务
 * 提供用户权限业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface UserPermissionService {

    List<FeatureObjectDto.ObjectPermission> listPermissions(Long userId);

    void updatePermissions(Long userId, List<FeatureObjectDto.Assignment> permissions);
}
