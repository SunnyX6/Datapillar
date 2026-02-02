package com.sunny.datapillar.admin.module.user.service.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.admin.module.user.dto.PermissionObjectDto;
import com.sunny.datapillar.admin.module.user.dto.RoleDto;
import com.sunny.datapillar.admin.module.user.entity.Permission;
import com.sunny.datapillar.admin.module.user.entity.Role;
import com.sunny.datapillar.admin.module.user.entity.RolePermission;
import com.sunny.datapillar.admin.module.user.mapper.PermissionMapper;
import com.sunny.datapillar.admin.module.user.mapper.PermissionObjectMapper;
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
    private final PermissionMapper permissionMapper;
    private final PermissionObjectMapper permissionObjectMapper;

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
        if (roleMapper.findByName(dto.getName()) != null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_ALREADY_EXISTS, dto.getName());
        }

        Role role = new Role();
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        String normalizedType = normalizeRoleType(dto.getType());
        role.setType(normalizedType == null ? "USER" : normalizedType);

        roleMapper.insert(role);

        if (dto.getPermissions() != null) {
            updateRolePermissions(role.getId(), dto.getPermissions());
        }

        log.info("Created role: id={}, name={}", role.getId(), role.getName());
        return role.getId();
    }

    @Override
    @Transactional
    public void updateRole(Long id, RoleDto.Update dto) {
        Role existingRole = roleMapper.selectById(id);
        if (existingRole == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, id);
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            Role roleWithSameName = roleMapper.findByName(dto.getName());
            if (roleWithSameName != null && !roleWithSameName.getId().equals(id)) {
                throw new BusinessException(ErrorCode.ADMIN_ROLE_ALREADY_EXISTS, dto.getName());
            }
            existingRole.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            existingRole.setDescription(dto.getDescription());
        }
        if (dto.getType() != null && !dto.getType().isBlank()) {
            existingRole.setType(normalizeRoleType(dto.getType()));
        }

        roleMapper.updateById(existingRole);

        if (dto.getPermissions() != null) {
            updateRolePermissions(id, dto.getPermissions());
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
    public List<PermissionObjectDto.ObjectPermission> getRolePermissions(Long roleId, String scope) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, roleId);
        }
        String normalizedScope = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
        if ("ASSIGNED".equals(normalizedScope)) {
            return permissionObjectMapper.selectRoleObjectPermissionsAssigned(roleId);
        }
        return permissionObjectMapper.selectRoleObjectPermissionsAll(roleId);
    }

    @Override
    @Transactional
    public void updateRolePermissions(Long roleId, List<PermissionObjectDto.Assignment> permissions) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, roleId);
        }
        roleMapper.deleteRolePermissions(roleId);

        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        Map<String, Long> permissionIdMap = getPermissionIdMap();
        Map<Long, String> uniqueAssignments = new LinkedHashMap<>();
        for (PermissionObjectDto.Assignment assignment : permissions) {
            if (assignment == null || assignment.getObjectId() == null) {
                continue;
            }
            uniqueAssignments.put(assignment.getObjectId(), assignment.getPermissionCode());
        }

        for (Map.Entry<Long, String> entry : uniqueAssignments.entrySet()) {
            String code = normalizePermissionCode(entry.getValue());
            if (code == null) {
                continue;
            }
            Long permissionId = permissionIdMap.get(code);
            if (permissionId == null) {
                throw new BusinessException(ErrorCode.ADMIN_INVALID_ARGUMENT, entry.getValue());
            }

            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setObjectId(entry.getKey());
            rolePermission.setPermissionId(permissionId);
            roleMapper.insertRolePermission(rolePermission);
        }
    }

    private Map<String, Long> getPermissionIdMap() {
        List<Permission> permissions = permissionMapper.selectList(null);
        Map<String, Long> map = new HashMap<>();
        if (permissions == null) {
            return map;
        }
        for (Permission permission : permissions) {
            if (permission.getCode() != null) {
                map.put(permission.getCode().toUpperCase(Locale.ROOT), permission.getId());
            }
        }
        return map;
    }

    private String normalizePermissionCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || "NONE".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeRoleType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }
}
