package com.sunny.datapillar.admin.module.user.service.impl;

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

import com.sunny.datapillar.admin.module.user.dto.PermissionObjectDto;
import com.sunny.datapillar.admin.module.user.dto.UserDto;
import com.sunny.datapillar.admin.module.user.entity.Permission;
import com.sunny.datapillar.admin.module.user.entity.User;
import com.sunny.datapillar.admin.module.user.entity.UserPermission;
import com.sunny.datapillar.admin.module.user.entity.UserRole;
import com.sunny.datapillar.admin.module.user.mapper.PermissionMapper;
import com.sunny.datapillar.admin.module.user.mapper.PermissionObjectMapper;
import com.sunny.datapillar.admin.module.user.mapper.UserMapper;
import com.sunny.datapillar.admin.module.user.service.UserService;
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
    private final PasswordEncoder passwordEncoder;
    private final PermissionMapper permissionMapper;
    private final PermissionObjectMapper permissionObjectMapper;

    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsernameWithRoles(username);
    }

    @Override
    public UserDto.Response getUserById(Long id) {
        User user = userMapper.selectById(id);
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
        if (userMapper.findByUsername(dto.getUsername()) != null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_ALREADY_EXISTS, dto.getUsername());
        }

        User user = new User();
        BeanUtils.copyProperties(dto, user);

        if (dto.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        user.setDeleted(0);

        userMapper.insert(user);

        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            assignRoles(user.getId(), dto.getRoleIds());
        }

        log.info("Created user: id={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    @Transactional
    public void updateUser(Long id, UserDto.Update dto) {
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, id);
        }

        if (dto.getUsername() != null) {
            User userWithSameName = userMapper.findByUsername(dto.getUsername());
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

        log.info("Updated user: id={}", id);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, id);
        }

        user.setDeleted(1);
        userMapper.updateById(user);
        userMapper.deleteUserRoles(id);
        userMapper.deleteUserPermissions(id);

        log.info("Deleted user: id={}", id);
    }

    @Override
    public List<UserDto.Response> getUserList() {
        List<User> users = userMapper.selectList(null);
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
        userMapper.deleteUserRoles(userId);

        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userMapper.insertUserRole(userRole);
            }
        }
    }

    @Override
    public List<String> getUserRoleCodes(Long userId) {
        return userMapper.getUserRoleCodes(userId);
    }

    @Override
    public List<String> getUserPermissionCodes(Long userId) {
        return userMapper.getUserPermissionCodes(userId);
    }

    @Override
    public List<PermissionObjectDto.ObjectPermission> getUserPermissions(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, userId);
        }
        List<PermissionObjectDto.ObjectPermission> objects = permissionObjectMapper.selectPermissionObjectsAll();
        List<PermissionObjectDto.RoleSource> roleSources = permissionObjectMapper.selectUserRoleSources(userId);
        List<PermissionObjectDto.Assignment> overrides = permissionObjectMapper.selectUserOverridePermissions(userId);

        Map<Long, List<PermissionObjectDto.RoleSource>> roleSourceMap = roleSources == null ? new HashMap<>()
                : roleSources.stream().collect(Collectors.groupingBy(PermissionObjectDto.RoleSource::getObjectId));
        Map<Long, String> overrideMap = new HashMap<>();
        if (overrides != null) {
            for (PermissionObjectDto.Assignment assignment : overrides) {
                if (assignment != null && assignment.getObjectId() != null) {
                    overrideMap.put(assignment.getObjectId(), normalizePermissionCode(assignment.getPermissionCode()));
                }
            }
        }

        List<PermissionObjectDto.ObjectPermission> result = new ArrayList<>();
        if (objects == null) {
            return result;
        }
        for (PermissionObjectDto.ObjectPermission object : objects) {
            Long objectId = object.getObjectId();
            List<PermissionObjectDto.RoleSource> sources = roleSourceMap.getOrDefault(objectId, new ArrayList<>());
            object.setRoleSources(sources);

            String rolePermission = calculateRoleMaxPermission(sources);
            String userOverride = overrideMap.get(objectId);
            object.setUserOverrideCode(userOverride);
            object.setPermissionCode(calculateMaxPermission(rolePermission, userOverride));
            result.add(object);
        }
        return result;
    }

    @Override
    @Transactional
    public void updateUserPermissions(Long userId, List<PermissionObjectDto.Assignment> permissions) {
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND, userId);
        }
        userMapper.deleteUserPermissions(userId);

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

            UserPermission userPermission = new UserPermission();
            userPermission.setUserId(userId);
            userPermission.setObjectId(entry.getKey());
            userPermission.setPermissionId(permissionId);
            userMapper.insertUserPermission(userPermission);
        }
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, UserDto.UpdateProfile dto) {
        User user = userMapper.selectById(userId);
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
        log.info("Updated profile: userId={}", userId);
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

    private String calculateRoleMaxPermission(List<PermissionObjectDto.RoleSource> sources) {
        int max = 0;
        if (sources != null) {
            for (PermissionObjectDto.RoleSource source : sources) {
                if (source == null) {
                    continue;
                }
                max = Math.max(max, permissionRank(source.getPermissionCode()));
            }
        }
        return permissionCodeByRank(max);
    }

    private String calculateMaxPermission(String rolePermission, String userOverride) {
        int max = Math.max(permissionRank(rolePermission), permissionRank(userOverride));
        return permissionCodeByRank(max);
    }

    private int permissionRank(String code) {
        if (code == null) {
            return 0;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if ("WRITE".equals(normalized)) {
            return 2;
        }
        if ("READ".equals(normalized)) {
            return 1;
        }
        return 0;
    }

    private String permissionCodeByRank(int rank) {
        if (rank == 2) {
            return "WRITE";
        }
        if (rank == 1) {
            return "READ";
        }
        return "NONE";
    }
}
