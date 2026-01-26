package com.sunny.datapillar.admin.module.user.service.impl;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.admin.module.user.dto.RoleDto;
import com.sunny.datapillar.admin.module.user.entity.Role;
import com.sunny.datapillar.admin.module.user.entity.RolePermission;
import com.sunny.datapillar.admin.module.user.mapper.RoleMapper;
import com.sunny.datapillar.admin.module.user.service.RoleService;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 角色服务实现类
 *
 * @author sunny
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
    public RoleDto.Response getRoleById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, id);
        }

        RoleDto.Response response = new RoleDto.Response();
        BeanUtils.copyProperties(role, response);
        return response;
    }

    @Override
    @Transactional
    public Long createRole(RoleDto.Create dto) {
        if (roleMapper.findByCode(dto.getCode()) != null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_ALREADY_EXISTS, dto.getCode());
        }

        Role role = new Role();
        BeanUtils.copyProperties(dto, role);

        roleMapper.insert(role);

        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            assignPermissions(role.getId(), dto.getPermissionIds());
        }

        log.info("Created role: id={}, code={}", role.getId(), role.getCode());
        return role.getId();
    }

    @Override
    @Transactional
    public void updateRole(Long id, RoleDto.Update dto) {
        Role existingRole = roleMapper.selectById(id);
        if (existingRole == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, id);
        }

        if (dto.getCode() != null) {
            Role roleWithSameCode = roleMapper.findByCode(dto.getCode());
            if (roleWithSameCode != null && !roleWithSameCode.getId().equals(id)) {
                throw new BusinessException(ErrorCode.ADMIN_ROLE_ALREADY_EXISTS, dto.getCode());
            }
            existingRole.setCode(dto.getCode());
        }
        if (dto.getName() != null) {
            existingRole.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            existingRole.setDescription(dto.getDescription());
        }

        roleMapper.updateById(existingRole);

        if (dto.getPermissionIds() != null) {
            assignPermissions(id, dto.getPermissionIds());
        }

        log.info("Updated role: id={}", id);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, id);
        }

        roleMapper.deleteById(id);
        roleMapper.deleteRolePermissions(id);
        roleMapper.deleteUserRolesByRoleId(id);

        log.info("Deleted role: id={}", id);
    }

    @Override
    public List<RoleDto.Response> getRoleList() {
        List<Role> roles = roleMapper.selectList(null);
        return roles.stream()
                .map(role -> {
                    RoleDto.Response dto = new RoleDto.Response();
                    BeanUtils.copyProperties(role, dto);
                    return dto;
                })
                .toList();
    }

    @Override
    public List<RoleDto.Response> getRolesByUserId(Long userId) {
        return roleMapper.findByUserId(userId).stream()
                .map(role -> {
                    RoleDto.Response dto = new RoleDto.Response();
                    BeanUtils.copyProperties(role, dto);
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        roleMapper.deleteRolePermissions(roleId);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermissionId(permissionId);
                roleMapper.insertRolePermission(rolePermission);
            }
        }
    }
}
