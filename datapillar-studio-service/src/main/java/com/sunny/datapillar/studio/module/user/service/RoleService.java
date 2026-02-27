package com.sunny.datapillar.studio.module.user.service;

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
 * 角色服务
 * 提供角色业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface RoleService {

    /**
     * 根据角色ID查询角色详情
     */
    RoleResponse getRoleById(Long id);

    /**
     * 创建角色
     */
    Long createRole(RoleCreateRequest dto);

    /**
     * 更新角色
     */
    void updateRole(Long id, RoleUpdateRequest dto);

    /**
     * 删除角色
     */
    void deleteRole(Long id);

    /**
     * 查询角色列表
     */
    List<RoleResponse> getRoleList();

    /**
     * 根据用户ID查询角色列表
     */
    List<RoleResponse> getRolesByUserId(Long userId);

    /**
     * 获取角色权限
     */
    List<FeatureObjectPermissionItem> getRolePermissions(Long roleId, String scope);

    /**
     * 更新角色权限（全量覆盖）
     */
    void updateRolePermissions(Long roleId, List<RoleFeatureAssignmentItem> permissions);

    /**
     * 获取角色成员
     */
    RoleMembersResponse getRoleMembers(Long roleId, Integer status);

    /**
     * 批量移除角色成员
     */
    void removeRoleMembers(Long roleId, List<Long> userIds);
}
