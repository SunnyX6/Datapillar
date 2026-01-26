package com.sunny.datapillar.admin.module.user.service.impl;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.admin.module.user.dto.UserDto;
import com.sunny.datapillar.admin.module.user.entity.User;
import com.sunny.datapillar.admin.module.user.entity.UserRole;
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
}
