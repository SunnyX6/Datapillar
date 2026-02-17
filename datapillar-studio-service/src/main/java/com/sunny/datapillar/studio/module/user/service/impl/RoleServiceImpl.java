package com.sunny.datapillar.studio.module.user.service.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.tenant.dto.FeatureEntitlementDto;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.tenant.util.PermissionLevelUtil;
import com.sunny.datapillar.studio.module.user.dto.RoleDto;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.RolePermission;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.service.RoleService;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;

/**
 * 角色服务实现
 * 实现角色业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
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
            throw new NotFoundException("角色不存在: roleId=%s", id);
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
            throw new AlreadyExistsException("角色代码已存在: %s", dto.getName());
        }

        Role role = new Role();
        role.setTenantId(tenantId);
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
            throw new NotFoundException("角色不存在: roleId=%s", id);
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            Role roleWithSameName = roleMapper.findByName(tenantId, dto.getName());
            if (roleWithSameName != null && !roleWithSameName.getId().equals(id)) {
                throw new AlreadyExistsException("角色代码已存在: %s", dto.getName());
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
            throw new NotFoundException("角色不存在: roleId=%s", id);
        }

        Long tenantId = getRequiredTenantId();
        roleMapper.deleteById(id);
        roleMapper.deleteRolePermissions(tenantId, id);
        roleMapper.deleteUserRolesByRoleId(tenantId, id);

        log.info("Deleted role: id={}", id);
    }

    @Override
    public List<RoleDto.Response> getRoleList() {
        Long tenantId = getRequiredTenantId();
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getTenantId, tenantId);
        List<Role> roles = roleMapper.selectList(wrapper);
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
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new NotFoundException("角色不存在: roleId=%s", roleId);
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
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new NotFoundException("角色不存在: roleId=%s", roleId);
        }
        Long tenantId = getRequiredTenantId();
        roleMapper.deleteRolePermissions(tenantId, roleId);

        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        Map<String, Permission> permissionMap = getPermissionMap();
        Map<Long, Permission> permissionByIdMap = getPermissionByIdMap(permissionMap);
        Map<Long, FeatureEntitlementDto.PermissionLimit> limitMap = getTenantPermissionLimitMap(tenantId);
        Map<Long, FeatureObjectDto.Assignment> uniqueAssignments = new LinkedHashMap<>();
        for (FeatureObjectDto.Assignment assignment : permissions) {
            if (assignment == null || assignment.getObjectId() == null) {
                continue;
            }
            uniqueAssignments.put(assignment.getObjectId(), assignment);
        }

        for (Map.Entry<Long, FeatureObjectDto.Assignment> entry : uniqueAssignments.entrySet()) {
            FeatureObjectDto.Assignment assignment = entry.getValue();
            Permission resolvedPermission = resolvePermission(assignment, permissionMap, permissionByIdMap);
            Long permissionId = resolvedPermission.getId();
            FeatureEntitlementDto.PermissionLimit limit = limitMap.get(entry.getKey());
            if (limit == null || limit.getStatus() == null || limit.getStatus() != 1) {
                throw new ForbiddenException("无权限访问");
            }
            int limitLevel = limit.getPermissionLevel() == null ? 0 : limit.getPermissionLevel();
            int permissionLevel = resolvedPermission.getLevel() == null ? 0 : resolvedPermission.getLevel();
            if (permissionLevel > limitLevel) {
                throw new ForbiddenException("无权限访问");
            }

            RolePermission rolePermission = new RolePermission();
            rolePermission.setTenantId(tenantId);
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

    private Map<Long, FeatureEntitlementDto.PermissionLimit> getTenantPermissionLimitMap(Long tenantId) {
        List<FeatureEntitlementDto.PermissionLimit> limits =
                tenantFeaturePermissionMapper.selectPermissionLimits(tenantId);
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
            throw new UnauthorizedException("未授权访问");
        }
        return tenantId;
    }

    private Map<Long, Permission> getPermissionByIdMap(Map<String, Permission> permissionMap) {
        Map<Long, Permission> map = new HashMap<>();
        if (permissionMap == null) {
            return map;
        }
        for (Permission permission : permissionMap.values()) {
            if (permission != null && permission.getId() != null) {
                map.put(permission.getId(), permission);
            }
        }
        return map;
    }

    private Permission resolvePermission(FeatureObjectDto.Assignment assignment,
                                         Map<String, Permission> permissionMap,
                                         Map<Long, Permission> permissionByIdMap) {
        if (assignment == null) {
            throw new BadRequestException("参数错误");
        }
        if (assignment.getPermissionId() != null) {
            Permission permission = permissionByIdMap.get(assignment.getPermissionId());
            if (permission == null) {
                throw new BadRequestException("参数错误", String.valueOf(assignment.getPermissionId()));
            }
            return permission;
        }
        String code = normalizePermissionCode(assignment.getPermissionCode());
        if (code == null) {
            throw new BadRequestException("参数错误");
        }
        Permission permission = permissionMap.get(code);
        if (permission == null) {
            throw new BadRequestException("参数错误", assignment.getPermissionCode());
        }
        return permission;
    }

}
