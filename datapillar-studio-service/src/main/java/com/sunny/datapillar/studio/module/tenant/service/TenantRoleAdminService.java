package com.sunny.datapillar.studio.module.tenant.service;

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
import java.util.List;

/**
 * 租户角色管理服务
 * 提供租户角色管理业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantRoleAdminService {

    List<RoleResponse> getRoleList();

    Long createRole(RoleCreateRequest dto);

    void updateRole(Long roleId, RoleUpdateRequest dto);

    void deleteRole(Long roleId);

    List<FeatureObjectPermissionItem> getRolePermissions(Long roleId, String scope);

    void updateRolePermissions(Long roleId, List<RoleFeatureAssignmentItem> permissions);

    RoleMembersResponse getRoleMembers(Long roleId, Integer status);

    void removeRoleMembers(Long roleId, List<Long> userIds);
}
