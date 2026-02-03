package com.sunny.datapillar.platform.module.user.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.platform.module.features.dto.FeatureEntitlementDto;
import com.sunny.datapillar.platform.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.platform.module.user.dto.UserDto;
import com.sunny.datapillar.platform.module.features.entity.Permission;
import com.sunny.datapillar.platform.module.user.entity.TenantUser;
import com.sunny.datapillar.platform.module.user.entity.User;
import com.sunny.datapillar.platform.module.user.entity.UserPermission;
import com.sunny.datapillar.platform.module.user.entity.UserRole;
import com.sunny.datapillar.platform.module.features.mapper.PermissionMapper;
import com.sunny.datapillar.platform.module.features.mapper.FeatureObjectMapper;
import com.sunny.datapillar.platform.module.features.mapper.TenantFeaturePermissionMapper;
import com.sunny.datapillar.platform.module.features.util.PermissionLevelUtil;
import com.sunny.datapillar.platform.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.platform.module.user.mapper.UserMapper;
import com.sunny.datapillar.platform.module.user.service.UserService;
import com.sunny.datapillar.platform.context.TenantContextHolder;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户服务实现类
 *
 * @author sunny
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final TenantUserMapper tenantUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionMapper permissionMapper;
    private final FeatureObjectMapper featureObjectMapper;
    private final TenantFeaturePermissionMapper tenantFeaturePermissionMapper;

    @Override
    public User findByUsername(String username) {
        Long tenantId = getRequiredTenantId();
        return userMapper.findByUsernameWithRoles(tenantId, username);
    }

    @Override
    public UserDto.Response getUserById(Long id) {
        Long tenantId = getRequiredTenantId();
        User user = userMapper.selectByIdAndTenantId(tenantId, id);
        if (user == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, id);
        }

        UserDto.Response response = new UserDto.Response();
        BeanUtils.copyProperties(user, response);
        response.setPermissions(getUserPermissionCodes(id));

        return response;
    }

    @Override
    @Transactional
    public Long createUser(UserDto.Create dto) {
        Long tenantId = getRequiredTenantId();
        User existing = userMapper.selectByUsernameGlobal(dto.getUsername());
        if (existing != null) {
            TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, existing.getId());
            if (tenantUser != null && tenantUser.getStatus() != null && tenantUser.getStatus() == 1) {
                throw new BusinessException(ErrorCode.ADMIN_USER_ALREADY_EXISTS, dto.getUsername());
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
    public void updateUser(Long id, UserDto.Update dto) {
        Long tenantId = getRequiredTenantId();
        User existingUser = userMapper.selectByIdAndTenantId(tenantId, id);
        if (existingUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, id);
        }

        if (dto.getUsername() != null) {
            User userWithSameName = userMapper.selectByUsernameGlobal(dto.getUsername());
            if (userWithSameName != null && !userWithSameName.getId().equals(id)) {
                throw new BusinessException(ErrorCode.ADMIN_USERNAME_IN_USE, dto.getUsername());
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
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, id);
        }

        tenantUserMapper.deleteByTenantIdAndUserId(tenantId, id);
        userMapper.deleteUserRoles(tenantId, id);
        userMapper.deleteUserPermissions(tenantId, id);

        log.info("Removed tenant member: tenantId={}, id={}", tenantId, id);
    }

    @Override
    public List<UserDto.Response> getUserList() {
        Long tenantId = getRequiredTenantId();
        List<User> users = userMapper.selectUsersByTenantId(tenantId);
        return users.stream()
                .map(user -> {
                    UserDto.Response response = new UserDto.Response();
                    BeanUtils.copyProperties(user, response);
                    response.setPermissions(getUserPermissionCodes(user.getId()));
                    return response;
                })
                .toList();
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        Long tenantId = getRequiredTenantId();
        if (userMapper.selectByIdAndTenantId(tenantId, userId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, userId);
        }
        userMapper.deleteUserRoles(tenantId, userId);

        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
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
    public List<FeatureObjectDto.ObjectPermission> getUserPermissions(Long userId) {
        Long tenantId = getRequiredTenantId();
        if (userMapper.selectByIdAndTenantId(tenantId, userId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, userId);
        }
        List<FeatureObjectDto.ObjectPermission> objects = featureObjectMapper.selectFeatureObjectsAll(tenantId);
        List<FeatureObjectDto.RoleSource> roleSources = featureObjectMapper.selectUserRoleSources(tenantId, userId);
        List<FeatureObjectDto.Assignment> overrides = featureObjectMapper.selectUserOverridePermissions(tenantId, userId);
        Map<String, Permission> permissionMap = getPermissionMap();

        Map<Long, List<FeatureObjectDto.RoleSource>> roleSourceMap = roleSources == null ? new HashMap<>()
                : roleSources.stream().collect(Collectors.groupingBy(FeatureObjectDto.RoleSource::getObjectId));
        Map<Long, String> overrideMap = new HashMap<>();
        if (overrides != null) {
            for (FeatureObjectDto.Assignment assignment : overrides) {
                if (assignment != null && assignment.getObjectId() != null) {
                    overrideMap.put(assignment.getObjectId(), normalizePermissionCode(assignment.getPermissionCode()));
                }
            }
        }

        List<FeatureObjectDto.ObjectPermission> result = new ArrayList<>();
        if (objects == null) {
            return result;
        }
        for (FeatureObjectDto.ObjectPermission object : objects) {
            Long objectId = object.getObjectId();
            List<FeatureObjectDto.RoleSource> sources = roleSourceMap.getOrDefault(objectId, new ArrayList<>());
            object.setRoleSources(sources);

            String rolePermission = calculateRoleMaxPermission(sources, permissionMap);
            String userOverride = overrideMap.get(objectId);
            object.setUserOverrideCode(userOverride);
            String maxPermission = calculateMaxPermission(permissionMap, rolePermission, userOverride);
            String tenantLimit = object.getTenantPermissionCode();
            object.setPermissionCode(PermissionLevelUtil.minCode(permissionMap, maxPermission, tenantLimit));
            result.add(object);
        }
        return result;
    }

    @Override
    @Transactional
    public void updateUserPermissions(Long userId, List<FeatureObjectDto.Assignment> permissions) {
        Long tenantId = getRequiredTenantId();
        if (userMapper.selectByIdAndTenantId(tenantId, userId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, userId);
        }
        userMapper.deleteUserPermissions(tenantId, userId);

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

            UserPermission userPermission = new UserPermission();
            userPermission.setTenantId(tenantId);
            userPermission.setUserId(userId);
            userPermission.setObjectId(entry.getKey());
            userPermission.setPermissionId(permissionId);
            userMapper.insertUserPermission(userPermission);
        }
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, UserDto.UpdateProfile dto) {
        Long tenantId = getRequiredTenantId();
        User user = userMapper.selectByIdAndTenantId(tenantId, userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, userId);
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
            throw new BusinessException(ErrorCode.ADMIN_UNAUTHORIZED);
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

    private String calculateRoleMaxPermission(List<FeatureObjectDto.RoleSource> sources,
                                              Map<String, Permission> permissionMap) {
        if (sources == null || sources.isEmpty()) {
            return "NONE";
        }
        List<String> codes = sources.stream()
                .map(FeatureObjectDto.RoleSource::getPermissionCode)
                .collect(Collectors.toList());
        return PermissionLevelUtil.maxCode(permissionMap, codes);
    }

    private String calculateMaxPermission(Map<String, Permission> permissionMap,
                                          String rolePermission,
                                          String userOverride) {
        return PermissionLevelUtil.maxCode(permissionMap, rolePermission, userOverride);
    }
}
