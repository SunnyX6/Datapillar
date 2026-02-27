package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import com.sunny.datapillar.studio.module.tenant.service.TenantRoleAdminService;
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
    public List<RoleResponse> getRoleList() {
        return roleService.getRoleList();
    }

    @Override
    public Long createRole(RoleCreateRequest dto) {
        return roleService.createRole(dto);
    }

    @Override
    public void updateRole(Long roleId, RoleUpdateRequest dto) {
        roleService.updateRole(roleId, dto);
    }

    @Override
    public void deleteRole(Long roleId) {
        roleService.deleteRole(roleId);
    }

    @Override
    public List<FeatureObjectPermissionItem> getRolePermissions(Long roleId, String scope) {
        return roleService.getRolePermissions(roleId, scope);
    }

    @Override
    public void updateRolePermissions(Long roleId, List<RoleFeatureAssignmentItem> permissions) {
        roleService.updateRolePermissions(roleId, permissions);
    }

    @Override
    public RoleMembersResponse getRoleMembers(Long roleId, Integer status) {
        return roleService.getRoleMembers(roleId, status);
    }

    @Override
    public void removeRoleMembers(Long roleId, List<Long> userIds) {
        roleService.removeRoleMembers(roleId, userIds);
    }
}
