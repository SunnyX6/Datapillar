package com.sunny.datapillar.studio.module.user.service.impl;

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
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObject;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObjectCategory;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectCategoryMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.studio.module.tenant.util.PermissionLevelUtil;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.RolePermission;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.module.user.service.RoleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.ConflictException;

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

    private static final int ROLE_LEVEL_PLATFORM_SUPER_ADMIN = 0;
    private static final int ROLE_LEVEL_DEFAULT = 100;
    private static final int STATUS_ENABLED = 1;
    private static final String ROLE_TYPE_USER = "USER";
    private static final String PERMISSION_CODE_READ = "READ";
    private static final String CATEGORY_CODE_MANAGE_DEFINE = "MANAGE_DEFINE";
    private static final String PROFILE_ROOT_PATH = "/profile";
    private static final String PROFILE_PERMISSION_PATH = "/profile/permission";
    private static final String PROFILE_AI_PATH = "/profile/llm/models";

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final FeatureObjectMapper featureObjectMapper;
    private final FeatureObjectCategoryMapper featureObjectCategoryMapper;
    private final TenantFeaturePermissionMapper tenantFeaturePermissionMapper;
    private final UserMapper userMapper;

    @Override
    public RoleResponse getRoleById(Long id) {
        Long tenantId = getRequiredTenantId();
        Role role = requireRoleInTenant(id, tenantId);

        RoleResponse response = new RoleResponse();
        BeanUtils.copyProperties(role, response);
        return response;
    }

    @Override
    @Transactional
    public Long createRole(RoleCreateRequest dto) {
        Long tenantId = getRequiredTenantId();
        if (roleMapper.findByName(tenantId, dto.getName()) != null) {
            throw new com.sunny.datapillar.common.exception.AlreadyExistsException("角色代码已存在: %s", dto.getName());
        }

        Role role = new Role();
        role.setTenantId(tenantId);
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        String normalizedType = normalizeRoleType(dto.getType());
        String roleType = normalizedType == null ? ROLE_TYPE_USER : normalizedType;
        role.setType(roleType);
        role.setLevel(ROLE_LEVEL_DEFAULT);
        Integer maxSort = roleMapper.selectMaxSortByTenant(tenantId);
        role.setSort((maxSort == null ? 0 : maxSort) + 1);

        roleMapper.insert(role);
        if (ROLE_TYPE_USER.equals(roleType) && role.getId() != null) {
            assignDefaultPermissionsForUserRole(tenantId, role.getId());
        }

        log.info("Created role: id={}, name={}", role.getId(), role.getName());
        return role.getId();
    }

    @Override
    @Transactional
    public void updateRole(Long id, RoleUpdateRequest dto) {
        Long tenantId = getRequiredTenantId();
        Role existingRole = requireRoleInTenant(id, tenantId);
        ensureRoleMutable(existingRole);

        if (dto.getName() != null && !dto.getName().isBlank()) {
            Role roleWithSameName = roleMapper.findByName(tenantId, dto.getName());
            if (roleWithSameName != null && !roleWithSameName.getId().equals(id)) {
                throw new com.sunny.datapillar.common.exception.AlreadyExistsException("角色代码已存在: %s", dto.getName());
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

        log.info("Updated role: id={}", id);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        Long tenantId = getRequiredTenantId();
        Role role = requireRoleInTenant(id, tenantId);
        ensureRoleMutable(role);
        long memberCount = roleMapper.countUsersByRoleId(tenantId, id);
        if (memberCount > 0) {
            throw new com.sunny.datapillar.common.exception.ConflictException("角色下存在成员，无法删除");
        }
        roleMapper.deleteRolePermissions(tenantId, id);
        roleMapper.deleteUserRolesByRoleId(tenantId, id);
        roleMapper.deleteById(id);

        log.info("Deleted role: id={}", id);
    }

    @Override
    public List<RoleResponse> getRoleList() {
        Long tenantId = getRequiredTenantId();
        return roleMapper.selectRoleListWithMemberCount(tenantId);
    }

    @Override
    public RoleMembersResponse getRoleMembers(Long roleId, Integer status) {
        Long tenantId = getRequiredTenantId();
        Role role = requireRoleInTenant(roleId, tenantId);
        List<RoleMemberItem> members = roleMapper.selectRoleMembers(tenantId, roleId, status);

        RoleMembersResponse response = new RoleMembersResponse();
        response.setRoleId(role.getId());
        response.setRoleName(role.getName());
        response.setRoleType(role.getType());
        response.setRoleLevel(role.getLevel());
        response.setRoleStatus(role.getStatus());
        response.setMemberCount((long) members.size());
        response.setMembers(members);
        return response;
    }

    @Override
    @Transactional
    public void removeRoleMembers(Long roleId, List<Long> userIds) {
        Long tenantId = getRequiredTenantId();
        requireRoleInTenant(roleId, tenantId);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        List<Long> uniqueUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uniqueUserIds.isEmpty()) {
            return;
        }

        List<Long> protectedUserIds = userMapper.selectUserIdsByMaxLevel(
                tenantId,
                uniqueUserIds,
                ROLE_LEVEL_PLATFORM_SUPER_ADMIN
        );
        if (protectedUserIds != null && !protectedUserIds.isEmpty()) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("平台超管用户不允许移除角色");
        }

        roleMapper.deleteRoleMembersByUserIds(tenantId, roleId, uniqueUserIds);
    }

    @Override
    public List<RoleResponse> getRolesByUserId(Long userId) {
        Long tenantId = getRequiredTenantId();
        return roleMapper.findByUserId(tenantId, userId).stream()
                .map(role -> {
                    RoleResponse dto = new RoleResponse();
                    BeanUtils.copyProperties(role, dto);
                    return dto;
                })
                .toList();
    }

    @Override
    public List<FeatureObjectPermissionItem> getRolePermissions(Long roleId, String scope) {
        Long tenantId = getRequiredTenantId();
        requireRoleInTenant(roleId, tenantId);
        String normalizedScope = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
        Map<String, Permission> permissionMap = getPermissionMap();
        if ("ASSIGNED".equals(normalizedScope)) {
            List<FeatureObjectPermissionItem> items =
                    featureObjectMapper.selectRoleObjectPermissionsAssigned(tenantId, roleId);
            applyTenantPermissionLimit(items, permissionMap);
            return buildPermissionTree(items);
        }
        List<FeatureObjectPermissionItem> items =
                featureObjectMapper.selectRoleObjectPermissionsAll(tenantId, roleId);
        applyTenantPermissionLimit(items, permissionMap);
        return buildPermissionTree(items);
    }

    @Override
    @Transactional
    public void updateRolePermissions(Long roleId, List<RoleFeatureAssignmentItem> permissions) {
        Long tenantId = getRequiredTenantId();
        Role role = requireRoleInTenant(roleId, tenantId);
        ensureRoleMutable(role);
        roleMapper.deleteRolePermissions(tenantId, roleId);

        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        Map<String, Permission> permissionMap = getPermissionMap();
        Map<Long, Permission> permissionByIdMap = getPermissionByIdMap(permissionMap);
        Map<Long, TenantFeaturePermissionLimitItem> limitMap = getTenantPermissionLimitMap(tenantId);
        Map<Long, RoleFeatureAssignmentItem> uniqueAssignments = new LinkedHashMap<>();
        for (RoleFeatureAssignmentItem assignment : permissions) {
            if (assignment == null || assignment.getObjectId() == null) {
                continue;
            }
            uniqueAssignments.put(assignment.getObjectId(), assignment);
        }

        for (Map.Entry<Long, RoleFeatureAssignmentItem> entry : uniqueAssignments.entrySet()) {
            RoleFeatureAssignmentItem assignment = entry.getValue();
            Permission resolvedPermission = resolvePermission(assignment, permissionMap, permissionByIdMap);
            Long permissionId = resolvedPermission.getId();
            TenantFeaturePermissionLimitItem limit = limitMap.get(entry.getKey());
            if (limit == null || limit.getStatus() == null || limit.getStatus() != 1) {
                throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
            }
            int limitLevel = limit.getPermissionLevel() == null ? 0 : limit.getPermissionLevel();
            int permissionLevel = resolvedPermission.getLevel() == null ? 0 : resolvedPermission.getLevel();
            if (permissionLevel > limitLevel) {
                throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
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

    private Map<Long, TenantFeaturePermissionLimitItem> getTenantPermissionLimitMap(Long tenantId) {
        List<TenantFeaturePermissionLimitItem> limits =
                tenantFeaturePermissionMapper.selectPermissionLimits(tenantId);
        Map<Long, TenantFeaturePermissionLimitItem> map = new HashMap<>();
        if (limits == null) {
            return map;
        }
        for (TenantFeaturePermissionLimitItem limit : limits) {
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

    private void assignDefaultPermissionsForUserRole(Long tenantId, Long roleId) {
        Permission readPermission = permissionMapper.selectSystemByCode(PERMISSION_CODE_READ);
        if (readPermission == null || readPermission.getId() == null) {
            throw new com.sunny.datapillar.common.exception.InternalException("服务器内部错误");
        }
        int readLevel = readPermission.getLevel() == null ? 0 : readPermission.getLevel();
        Long manageDefineCategoryId = resolveManageDefineCategoryId();
        Map<Long, TenantFeaturePermissionLimitItem> limitMap = getTenantPermissionLimitMap(tenantId);

        LambdaQueryWrapper<FeatureObject> featureObjectQuery = new LambdaQueryWrapper<>();
        featureObjectQuery.eq(FeatureObject::getStatus, STATUS_ENABLED)
                .orderByAsc(FeatureObject::getSort)
                .orderByAsc(FeatureObject::getId);
        List<FeatureObject> featureObjects = featureObjectMapper.selectList(featureObjectQuery);
        if (featureObjects == null || featureObjects.isEmpty()) {
            return;
        }

        for (FeatureObject object : featureObjects) {
            if (object == null || object.getId() == null || !shouldAssignDefaultPermission(object, manageDefineCategoryId)) {
                continue;
            }
            TenantFeaturePermissionLimitItem permissionLimit = limitMap.get(object.getId());
            if (permissionLimit == null || permissionLimit.getStatus() == null || permissionLimit.getStatus() != STATUS_ENABLED) {
                continue;
            }
            int limitLevel = permissionLimit.getPermissionLevel() == null ? 0 : permissionLimit.getPermissionLevel();
            if (limitLevel < readLevel) {
                continue;
            }

            RolePermission rolePermission = new RolePermission();
            rolePermission.setTenantId(tenantId);
            rolePermission.setRoleId(roleId);
            rolePermission.setObjectId(object.getId());
            rolePermission.setPermissionId(readPermission.getId());
            roleMapper.insertRolePermission(rolePermission);
        }
    }

    private Long resolveManageDefineCategoryId() {
        FeatureObjectCategory category = featureObjectCategoryMapper.selectByCode(CATEGORY_CODE_MANAGE_DEFINE);
        return category == null ? null : category.getId();
    }

    private boolean shouldAssignDefaultPermission(FeatureObject object, Long manageDefineCategoryId) {
        String path = object.getPath() == null ? null : object.getPath().trim();
        if (PROFILE_PERMISSION_PATH.equals(path) || PROFILE_AI_PATH.equals(path)) {
            return false;
        }
        if (PROFILE_ROOT_PATH.equals(path) || (path != null && path.startsWith(PROFILE_ROOT_PATH + "/"))) {
            return true;
        }
        return manageDefineCategoryId == null || !manageDefineCategoryId.equals(object.getCategoryId());
    }

    private Role requireRoleInTenant(Long roleId, Long tenantId) {
        LambdaQueryWrapper<Role> query = new LambdaQueryWrapper<>();
        query.eq(Role::getId, roleId)
                .eq(Role::getTenantId, tenantId);
        Role role = roleMapper.selectOne(query);
        if (role == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("角色不存在: roleId=%s", roleId);
        }
        return role;
    }

    private void ensureRoleMutable(Role role) {
        if (role != null && role.getLevel() != null && role.getLevel() <= ROLE_LEVEL_PLATFORM_SUPER_ADMIN) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("平台超管角色不允许修改或删除");
        }
    }

    private void applyTenantPermissionLimit(List<FeatureObjectPermissionItem> items,
                                            Map<String, Permission> permissionMap) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (FeatureObjectPermissionItem item : items) {
            String tenantCode = item.getTenantPermissionCode();
            String currentCode = item.getPermissionCode();
            String effective = PermissionLevelUtil.minCode(permissionMap, currentCode, tenantCode);
            item.setPermissionCode(effective);
        }
    }

    private List<FeatureObjectPermissionItem> buildPermissionTree(List<FeatureObjectPermissionItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, FeatureObjectPermissionItem> nodeMap = new LinkedHashMap<>();
        for (FeatureObjectPermissionItem item : items) {
            if (item == null || item.getObjectId() == null) {
                continue;
            }
            item.setChildren(new ArrayList<>());
            nodeMap.put(item.getObjectId(), item);
        }

        List<FeatureObjectPermissionItem> roots = new ArrayList<>();
        for (FeatureObjectPermissionItem item : items) {
            if (item == null || item.getObjectId() == null) {
                continue;
            }
            Long parentId = item.getParentId();
            if (parentId == null) {
                roots.add(item);
                continue;
            }
            FeatureObjectPermissionItem parent = nodeMap.get(parentId);
            if (parent == null) {
                roots.add(item);
                continue;
            }
            parent.getChildren().add(item);
        }

        sortPermissionNodes(roots);
        return roots;
    }

    private void sortPermissionNodes(List<FeatureObjectPermissionItem> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        nodes.sort(Comparator.comparingInt((FeatureObjectPermissionItem item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparing(item -> item.getObjectId() == null ? 0L : item.getObjectId()));

        for (FeatureObjectPermissionItem node : nodes) {
            sortPermissionNodes(node.getChildren());
        }
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("未授权访问");
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

    private Permission resolvePermission(RoleFeatureAssignmentItem assignment,
                                         Map<String, Permission> permissionMap,
                                         Map<Long, Permission> permissionByIdMap) {
        if (assignment == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        if (assignment.getPermissionId() != null) {
            Permission permission = permissionByIdMap.get(assignment.getPermissionId());
            if (permission == null) {
                throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误", String.valueOf(assignment.getPermissionId()));
            }
            return permission;
        }
        String code = normalizePermissionCode(assignment.getPermissionCode());
        if (code == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        Permission permission = permissionMap.get(code);
        if (permission == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误", assignment.getPermissionCode());
        }
        return permission;
    }

}
