package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantRoleAdminService;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.service.RoleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户角色管理服务实现
 * 实现租户角色管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantRoleAdminServiceImpl implements TenantRoleAdminService {

    private final RoleService roleService;

    @Override
    public List<RoleDto.Response> getRoleList() {
        return roleService.getRoleList();
    }

    @Override
    public Long createRole(RoleDto.Create dto) {
        return roleService.createRole(dto);
    }

    @Override
    public void updateRole(Long roleId, RoleDto.Update dto) {
        roleService.updateRole(roleId, dto);
    }

    @Override
    public void deleteRole(Long roleId) {
        roleService.deleteRole(roleId);
    }

    @Override
    public List<FeatureObjectDto.ObjectPermission> getRolePermissions(Long roleId, String scope) {
        return roleService.getRolePermissions(roleId, scope);
    }

    @Override
    public void updateRolePermissions(Long roleId, List<FeatureObjectDto.Assignment> permissions) {
        roleService.updateRolePermissions(roleId, permissions);
    }
}
