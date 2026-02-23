package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import java.util.List;

/**
 * 租户角色管理服务
 * 提供租户角色管理业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantRoleAdminService {

    List<RoleDto.Response> getRoleList();

    Long createRole(RoleDto.Create dto);

    void updateRole(Long roleId, RoleDto.Update dto);

    void deleteRole(Long roleId);

    List<FeatureObjectDto.ObjectPermission> getRolePermissions(Long roleId, String scope);

    void updateRolePermissions(Long roleId, List<FeatureObjectDto.Assignment> permissions);

    RoleDto.MembersResponse getRoleMembers(Long roleId, Integer status);
}
