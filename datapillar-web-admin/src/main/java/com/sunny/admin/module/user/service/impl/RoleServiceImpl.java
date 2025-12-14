package com.sunny.admin.module.user.service.impl;

import com.sunny.admin.response.WebAdminErrorCode;
import com.sunny.admin.response.WebAdminException;
import com.sunny.admin.module.user.dto.RoleReqDto;
import com.sunny.admin.module.user.dto.RoleRespDto;
import com.sunny.admin.module.user.entity.Role;
import com.sunny.admin.module.user.entity.RolePermission;
import com.sunny.admin.module.user.mapper.RoleMapper;
import com.sunny.admin.module.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色服务实现类
 *
 * @author sunny
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    
    private final RoleMapper roleMapper;
    
    @Override
    public Role findByCode(String code) {
        return roleMapper.findByCode(code);
    }
    
    @Override
    public RoleRespDto getRoleById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new WebAdminException(WebAdminErrorCode.ROLE_NOT_FOUND, id);
        }

        RoleRespDto response = new RoleRespDto();
        BeanUtils.copyProperties(role, response);
        return response;
    }
    
    @Override
    @Transactional
    public RoleRespDto createRole(RoleReqDto request) {
        // 检查角色代码是否已存在
        if (roleMapper.findByCode(request.getCode()) != null) {
            throw new WebAdminException(WebAdminErrorCode.ROLE_ALREADY_EXISTS, request.getCode());
        }
        
        Role role = new Role();
        BeanUtils.copyProperties(request, role);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        
        roleMapper.insert(role);
        
        // 分配权限
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            assignPermissions(role.getId(), request.getPermissionIds());
        }
        
        return getRoleById(role.getId());
    }
    
    @Override
    @Transactional
    public RoleRespDto updateRole(Long id, RoleReqDto request) {
        Role existingRole = roleMapper.selectById(id);
        if (existingRole == null) {
            throw new WebAdminException(WebAdminErrorCode.ROLE_NOT_FOUND, id);
        }

        // 检查角色代码是否被其他角色使用
        Role roleWithSameCode = roleMapper.findByCode(request.getCode());
        if (roleWithSameCode != null && !roleWithSameCode.getId().equals(id)) {
            throw new WebAdminException(WebAdminErrorCode.ROLE_ALREADY_EXISTS, request.getCode());
        }
        
        BeanUtils.copyProperties(request, existingRole, "id", "createdAt");
        existingRole.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(existingRole);
        
        // 更新权限
        if (request.getPermissionIds() != null) {
            assignPermissions(id, request.getPermissionIds());
        }
        
        return getRoleById(id);
    }
    
    @Override
    @Transactional
    public void deleteRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new WebAdminException(WebAdminErrorCode.ROLE_NOT_FOUND, id);
        }
        
        // 删除角色
        roleMapper.deleteById(id);
        
        // 删除角色权限关联
        roleMapper.deleteRolePermissions(id);
        
        // 删除用户角色关联
        roleMapper.deleteUserRolesByRoleId(id);
    }
    
    @Override
    public List<RoleRespDto> getRoleList() {
        List<Role> roles = roleMapper.selectList(null);
        return roles.stream()
                .map(role -> {
                    RoleRespDto dto = new RoleRespDto();
                    BeanUtils.copyProperties(role, dto);
                    return dto;
                })
                .toList();
    }
    
    @Override
    public List<Role> getRolesByUserId(Long userId) {
        return roleMapper.findByUserId(userId);
    }
    
    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // 先删除现有权限
        roleMapper.deleteRolePermissions(roleId);
        
        // 添加新权限
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermissionId(permissionId);
                rolePermission.setCreatedAt(LocalDateTime.now());
                roleMapper.insertRolePermission(rolePermission);
            }
        }
    }
}