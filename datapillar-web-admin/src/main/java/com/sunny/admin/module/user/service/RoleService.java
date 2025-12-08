package com.sunny.admin.module.user.service;

import com.sunny.admin.module.user.dto.RoleReqDto;
import com.sunny.admin.module.user.dto.RoleRespDto;
import com.sunny.admin.module.user.entity.Role;

import java.util.List;

/**
 * 角色服务接口
 * 
 * @author sunny
 * @since 2024-01-01
 */
public interface RoleService {
    
    /**
     * 根据角色代码查询角色
     */
    Role findByCode(String code);
    
    /**
     * 根据角色ID查询角色详情
     */
    RoleRespDto getRoleById(Long id);
    
    /**
     * 创建角色
     */
    RoleRespDto createRole(RoleReqDto request);
    
    /**
     * 更新角色
     */
    RoleRespDto updateRole(Long id, RoleReqDto request);
    
    /**
     * 删除角色
     */
    void deleteRole(Long id);
    
    /**
     * 查询角色列表
     */
    List<RoleRespDto> getRoleList();
    
    /**
     * 根据用户ID查询角色列表
     */
    List<Role> getRolesByUserId(Long userId);
    
    /**
     * 为角色分配权限
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);
}