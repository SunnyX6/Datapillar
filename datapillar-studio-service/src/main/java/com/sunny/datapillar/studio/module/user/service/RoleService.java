package com.sunny.datapillar.studio.module.user.service;

import java.util.List;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;

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
    RoleDto.Response getRoleById(Long id);

    /**
     * 创建角色
     */
    Long createRole(RoleDto.Create dto);

    /**
     * 更新角色
     */
    void updateRole(Long id, RoleDto.Update dto);

    /**
     * 删除角色
     */
    void deleteRole(Long id);

    /**
     * 查询角色列表
     */
    List<RoleDto.Response> getRoleList();

    /**
     * 根据用户ID查询角色列表
     */
    List<RoleDto.Response> getRolesByUserId(Long userId);

    /**
     * 获取角色权限
     */
    List<FeatureObjectDto.ObjectPermission> getRolePermissions(Long roleId, String scope);

    /**
     * 更新角色权限（全量覆盖）
     */
    void updateRolePermissions(Long roleId, List<FeatureObjectDto.Assignment> permissions);

    /**
     * 获取角色成员
     */
    RoleDto.MembersResponse getRoleMembers(Long roleId, Integer status);
}
