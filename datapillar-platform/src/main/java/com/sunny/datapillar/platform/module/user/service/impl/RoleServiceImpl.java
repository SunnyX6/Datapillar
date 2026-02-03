package com.sunny.datapillar.platform.module.user.service.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.platform.context.TenantContextHolder;
import com.sunny.datapillar.platform.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.platform.module.features.dto.FeatureEntitlementDto;
import com.sunny.datapillar.platform.module.features.entity.Permission;
import com.sunny.datapillar.platform.module.features.mapper.FeatureObjectMapper;
import com.sunny.datapillar.platform.module.features.mapper.PermissionMapper;
import com.sunny.datapillar.platform.module.features.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.platform.module.features.util.PermissionLevelUtil;
import com.sunny.datapillar.platform.module.user.dto.RoleDto;
import com.sunny.datapillar.platform.module.user.entity.Role;
import com.sunny.datapillar.platform.module.user.entity.RolePermission;
import com.sunny.datapillar.platform.module.user.mapper.RoleMapper;
import com.sunny.datapillar.platform.module.user.service.RoleService;
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
    private final FeatureObjectMapper featureObjectMapper;
    private final TenantFeaturePermissionMapper tenantFeaturePermissionMapper;

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
        Long tenantId = getRequiredTenantId();
        if (roleMapper.findByName(tenantId, dto.getName()) != null) {
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
        Long tenantId = getRequiredTenantId();
        Role existingRole = roleMapper.selectById(id);
        if (existingRole == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, id);
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            Role roleWithSameName = roleMapper.findByName(tenantId, dto.getName());
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

        Long tenantId = getRequiredTenantId();
        roleMapper.deleteById(id);
        roleMapper.deleteRolePermissions(tenantId, id);
        roleMapper.deleteUserRolesByRoleId(tenantId, id);

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
        Long tenantId = getRequiredTenantId();
        return roleMapper.findByUserId(tenantId, userId).stream()
                .map(role -> {
                    RoleDto.Response dto = new RoleDto.Response();
                    BeanUtils.copyProperties(role, dto);
                    return dto;
                })
                .toList();
    }

    @Override
    public List<FeatureObjectDto.ObjectPermission> getRolePermissions(Long roleId, String scope) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, roleId);
        }
        Long tenantId = getRequiredTenantId();
        String normalizedScope = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
        Map<String, Permission> permissionMap = getPermissionMap();
        if ("ASSIGNED".equals(normalizedScope)) {
            List<FeatureObjectDto.ObjectPermission> items =
                    featureObjectMapper.selectRoleObjectPermissionsAssigned(tenantId, roleId);
            applyTenantPermissionLimit(items, permissionMap);
            return items;
        }
        List<FeatureObjectDto.ObjectPermission> items =
                featureObjectMapper.selectRoleObjectPermissionsAll(tenantId, roleId);
        applyTenantPermissionLimit(items, permissionMap);
        return items;
    }

    @Override
    @Transactional
    public void updateRolePermissions(Long roleId, List<FeatureObjectDto.Assignment> permissions) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_NOT_FOUND, roleId);
        }
        Long tenantId = getRequiredTenantId();
        roleMapper.deleteRolePermissions(tenantId, roleId);

        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        Map<String, Permission> permissionMap = getPermissionMap();
        Map<String, Long> permissionIdMap = getPermissionIdMap(permissionMap);
        Map<Long, FeatureEntitlementDto.PermissionLimit> limitMap = getTenantPermissionLimitMap(tenantId);
        Map<Long, String> uniqueAssignments = new LinkedHashMap<>();
        for (FeatureObjectDto.Assignment assignment : permissions) {
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
            Permission permission = permissionMap.get(code);
            Long permissionId = permissionIdMap.get(code);
            if (permissionId == null) {
                throw new BusinessException(ErrorCode.ADMIN_INVALID_ARGUMENT, entry.getValue());
            }
            FeatureEntitlementDto.PermissionLimit limit = limitMap.get(entry.getKey());
            if (limit == null || limit.getStatus() == null || limit.getStatus() != 1) {
                throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
            }
            int limitLevel = limit.getPermissionLevel() == null ? 0 : limit.getPermissionLevel();
            int permissionLevel = permission.getLevel() == null ? 0 : permission.getLevel();
            if (permissionLevel > limitLevel) {
                throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
            }

            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setObjectId(entry.getKey());
            rolePermission.setPermissionId(permissionId);
            roleMapper.insertRolePermission(rolePermission);
        }
    }

    private Map<String, Permission> getPermissionMap() {
        List<Permission> permissions = permissionMapper.selectSystemPermissions();
        Map<String, Permission> map = new HashMap<>();
        if (permissions == null) {
            return map;
        }
        for (Permission permission : permissions) {
            if (permission.getCode() != null) {
                map.put(permission.getCode().toUpperCase(Locale.ROOT), permission);
            }
        }
        return map;
    }

    private Map<String, Long> getPermissionIdMap(Map<String, Permission> permissionMap) {
        Map<String, Long> map = new HashMap<>();
        if (permissionMap == null) {
            return map;
        }
        for (Map.Entry<String, Permission> entry : permissionMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getId() != null) {
                map.put(entry.getKey(), entry.getValue().getId());
            }
        }
        return map;
    }

    private Map<Long, FeatureEntitlementDto.PermissionLimit> getTenantPermissionLimitMap(Long tenantId) {
        List<FeatureEntitlementDto.PermissionLimit> limits = tenantFeaturePermissionMapper.selectPermissionLimits(tenantId);
        Map<Long, FeatureEntitlementDto.PermissionLimit> map = new HashMap<>();
        if (limits == null) {
            return map;
        }
        for (FeatureEntitlementDto.PermissionLimit limit : limits) {
            if (limit != null && limit.getObjectId() != null) {
                map.put(limit.getObjectId(), limit);
            }
        }
        return map;
    }

    private String normalizePermissionCode(String code) {
        return PermissionLevelUtil.normalizeCode(code);
    }

    private String normalizeRoleType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private void applyTenantPermissionLimit(List<FeatureObjectDto.ObjectPermission> items,
                                            Map<String, Permission> permissionMap) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (FeatureObjectDto.ObjectPermission item : items) {
            String tenantCode = item.getTenantPermissionCode();
            String currentCode = item.getPermissionCode();
            String effective = PermissionLevelUtil.minCode(permissionMap, currentCode, tenantCode);
            item.setPermissionCode(effective);
        }
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.ADMIN_UNAUTHORIZED);
        }
        return tenantId;
    }
}
