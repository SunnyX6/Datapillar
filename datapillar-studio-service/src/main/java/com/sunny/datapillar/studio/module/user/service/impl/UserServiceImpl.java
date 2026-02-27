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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.UserRole;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.util.PermissionLevelUtil;
import com.sunny.datapillar.studio.module.user.mapper.RoleMapper;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.module.user.service.UserService;
import com.sunny.datapillar.studio.context.TenantContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.ConflictException;

/**
 * 用户服务实现
 * 实现用户业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int USER_LEVEL_PLATFORM_SUPER_ADMIN = 0;
    private static final int USER_LEVEL_DEFAULT = 100;
    private static final int MEMBER_STATUS_ENABLED = 1;
    private static final int MEMBER_STATUS_DISABLED = 0;

    private final UserMapper userMapper;
    private final TenantUserMapper tenantUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionMapper permissionMapper;
    private final FeatureObjectMapper featureObjectMapper;
    private final RoleMapper roleMapper;

    @Override
    public User findByUsername(String username) {
        Long tenantId = getRequiredTenantId();
        return userMapper.findByUsernameWithRoles(tenantId, username);
    }

    @Override
    public UserResponse getUserById(Long id) {
        Long tenantId = getRequiredTenantId();
        User user = userMapper.selectByIdAndTenantId(tenantId, id);
        if (user == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", id);
        }

        UserResponse response = new UserResponse();
        BeanUtils.copyProperties(user, response);
        return response;
    }

    @Override
    @Transactional
    public Long createUser(UserCreateRequest dto) {
        Long tenantId = getRequiredTenantId();
        User existing = userMapper.selectByUsernameGlobal(dto.getUsername());
        if (existing != null) {
            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, existing.getId());
            if (tenantUser != null && tenantUser.getStatus() != null && tenantUser.getStatus() == 1) {
                throw new com.sunny.datapillar.common.exception.AlreadyExistsException("用户名已存在: %s", dto.getUsername());
            }
            if (tenantUser == null) {
                tenantUser = new TenantUser();
                tenantUser.setTenantId(tenantId);
                tenantUser.setUserId(existing.getId());
                tenantUser.setStatus(1);
                tenantUser.setIsDefault(0);
                tenantUser.setJoinedAt(java.time.LocalDateTime.now());
                tenantUserMapper.insert(tenantUser);
            } else {
                tenantUser.setStatus(1);
                tenantUserMapper.updateById(tenantUser);
            }

            if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
                assignRoles(existing.getId(), dto.getRoleIds());
            }
            log.info("Added tenant member: tenantId={}, userId={}", tenantId, existing.getId());
            return existing.getId();
        }

        User user = new User();
        BeanUtils.copyProperties(dto, user);
        user.setTenantId(tenantId);

        if (dto.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        user.setLevel(USER_LEVEL_DEFAULT);
        user.setDeleted(0);

        userMapper.insert(user);

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(tenantId);
        tenantUser.setUserId(user.getId());
        tenantUser.setStatus(1);
        tenantUser.setIsDefault(tenantUserMapper.countByUserId(user.getId()) == 0 ? 1 : 0);
        tenantUser.setJoinedAt(java.time.LocalDateTime.now());
        tenantUserMapper.insert(tenantUser);

        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            assignRoles(user.getId(), dto.getRoleIds());
        }

        log.info("Created user: tenantId={}, id={}, username={}", tenantId, user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    @Transactional
    public void updateUser(Long id, UserUpdateRequest dto) {
        Long tenantId = getRequiredTenantId();
        User existingUser = userMapper.selectByIdAndTenantId(tenantId, id);
        if (existingUser == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", id);
        }

        if (dto.getUsername() != null) {
            User userWithSameName = userMapper.selectByUsernameGlobal(dto.getUsername());
            if (userWithSameName != null && !userWithSameName.getId().equals(id)) {
                throw new com.sunny.datapillar.common.exception.ConflictException("用户名已被其他用户使用: %s", dto.getUsername());
            }
            existingUser.setUsername(dto.getUsername());
        }

        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getNickname() != null) {
            existingUser.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            existingUser.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            existingUser.setPhone(dto.getPhone());
        }
        if (dto.getStatus() != null) {
            existingUser.setStatus(dto.getStatus());
        }

        userMapper.updateById(existingUser);

        if (dto.getRoleIds() != null) {
            assignRoles(id, dto.getRoleIds());
        }

        log.info("Updated user: tenantId={}, id={}", tenantId, id);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        Long tenantId = getRequiredTenantId();
        User user = userMapper.selectByIdAndTenantId(tenantId, id);
        if (user == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", id);
        }
        if (user.getLevel() != null && user.getLevel() <= USER_LEVEL_PLATFORM_SUPER_ADMIN) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("平台超管用户不允许删除");
        }

        tenantUserMapper.deleteByTenantIdAndUserId(tenantId, id);
        userMapper.deleteUserRoles(tenantId, id);

        log.info("Removed tenant member: tenantId={}, id={}", tenantId, id);
    }

    @Override
    public List<UserResponse> getUserList() {
        Long tenantId = getRequiredTenantId();
        List<User> users = userMapper.selectUsersByTenantId(tenantId);
        return users.stream()
                .map(user -> {
                    UserResponse response = new UserResponse();
                    BeanUtils.copyProperties(user, response);
                    return response;
                })
                .toList();
    }

    @Override
    public List<User> listUsers(Integer status) {
        Long tenantId = getRequiredTenantId();
        return userMapper.selectUsersByTenantIdAndStatus(tenantId, status);
    }

    @Override
    @Transactional
    public void updateTenantMemberStatus(Long userId, Integer status) {
        Long tenantId = getRequiredTenantId();
        if (userId == null
                || status == null
                || (status != MEMBER_STATUS_ENABLED && status != MEMBER_STATUS_DISABLED)) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, userId);
        if (tenantUser == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", userId);
        }
        tenantUser.setStatus(status);
        tenantUserMapper.updateById(tenantUser);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        Long tenantId = getRequiredTenantId();
        if (userMapper.selectByIdAndTenantId(tenantId, userId) == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", userId);
        }
        userMapper.deleteUserRoles(tenantId, userId);

        if (roleIds != null && !roleIds.isEmpty()) {
            java.util.Set<Long> uniqueRoles = new java.util.HashSet<>(roleIds);
            for (Long roleId : uniqueRoles) {
                Role role = roleMapper.selectById(roleId);
                if (role == null || !tenantId.equals(role.getTenantId())) {
                    throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
                }
                UserRole userRole = new UserRole();
                userRole.setTenantId(tenantId);
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userMapper.insertUserRole(userRole);
            }
        }
    }

    @Override
    public List<String> getUserRoleCodes(Long userId) {
        Long tenantId = getRequiredTenantId();
        return userMapper.getUserRoleCodes(tenantId, userId);
    }

    @Override
    public List<String> getUserPermissionCodes(Long userId) {
        Long tenantId = getRequiredTenantId();
        return userMapper.getUserPermissionCodes(tenantId, userId);
    }

    @Override
    public List<FeatureObjectPermissionItem> getUserPermissions(Long userId) {
        Long tenantId = getRequiredTenantId();
        if (userMapper.selectByIdAndTenantId(tenantId, userId) == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", userId);
        }
        List<FeatureObjectPermissionItem> objects = featureObjectMapper.selectFeatureObjectsAll(tenantId);
        List<FeatureRoleSourceItem> roleSources =
                featureObjectMapper.selectUserRoleSources(tenantId, userId);
        Map<String, Permission> permissionMap = getPermissionMap();

        Map<Long, List<FeatureRoleSourceItem>> roleSourceMap = roleSources == null ? new HashMap<>()
                : roleSources.stream().collect(Collectors.groupingBy(FeatureRoleSourceItem::getObjectId));

        List<FeatureObjectPermissionItem> result = new ArrayList<>();
        if (objects == null) {
            return result;
        }
        for (FeatureObjectPermissionItem object : objects) {
            Long objectId = object.getObjectId();
            List<FeatureRoleSourceItem> sources = roleSourceMap.getOrDefault(objectId, new ArrayList<>());

            String rolePermission = calculateRoleMaxPermission(sources, permissionMap);
            String tenantLimit = object.getTenantPermissionCode();
            object.setPermissionCode(PermissionLevelUtil.minCode(permissionMap, rolePermission, tenantLimit));
            result.add(object);
        }
        return result;
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, UserProfileUpdateRequest dto) {
        Long tenantId = getRequiredTenantId();
        User user = userMapper.selectByIdAndTenantId(tenantId, userId);
        if (user == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", userId);
        }

        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }

        userMapper.updateById(user);
        log.info("Updated profile: tenantId={}, userId={}", tenantId, userId);
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("未授权访问");
        }
        return tenantId;
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

    private String calculateRoleMaxPermission(List<FeatureRoleSourceItem> sources,
                                              Map<String, Permission> permissionMap) {
        if (sources == null || sources.isEmpty()) {
            return "DISABLE";
        }
        List<String> codes = sources.stream()
                .map(FeatureRoleSourceItem::getPermissionCode)
                .collect(Collectors.toList());
        return PermissionLevelUtil.maxCode(permissionMap, codes);
    }
}
