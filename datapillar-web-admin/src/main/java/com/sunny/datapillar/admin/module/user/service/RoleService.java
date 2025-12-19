package com.sunny.datapillar.admin.module.user.service;

import java.util.List;

import com.sunny.datapillar.admin.module.user.dto.RoleDto;
import com.sunny.datapillar.admin.module.user.entity.Role;

/**
 * 角色服务接口
 *
 * @author sunny
 */
public interface RoleService {

    /**
     * 根据角色代码查询角色
     */
    Role findByCode(String code);

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
     * 为角色分配权限
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);
}
