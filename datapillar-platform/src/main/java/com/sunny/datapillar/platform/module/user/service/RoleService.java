package com.sunny.datapillar.platform.module.user.service;

import java.util.List;

import com.sunny.datapillar.platform.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.platform.module.user.dto.RoleDto;

/**
 * 角色服务接口
 *
 * @author sunny
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
}
