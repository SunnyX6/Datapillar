package com.sunny.admin.module.user.service.impl;

import com.sunny.admin.response.WebAdminErrorCode;
import com.sunny.admin.response.WebAdminException;
import com.sunny.admin.module.user.dto.UserReqDto;
import com.sunny.admin.module.user.dto.UserRespDto;
import com.sunny.admin.module.user.dto.UpdateProfileReqDto;
import com.sunny.admin.module.user.entity.User;
import com.sunny.admin.module.user.entity.UserRole;
import com.sunny.admin.module.user.mapper.UserMapper;
import com.sunny.admin.module.user.service.RoleService;
import com.sunny.admin.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现类
 *
 * @author sunny
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserMapper userMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsernameWithRoles(username);
    }
    
    @Override
    public UserRespDto getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new WebAdminException(WebAdminErrorCode.USER_NOT_FOUND, id);
        }
        
        UserRespDto response = new UserRespDto();
        BeanUtils.copyProperties(user, response);
        response.setPermissions(getUserPermissionCodes(id));
        
        return response;
    }
    
    @Override
    @Transactional
    public UserRespDto createUser(UserReqDto request) {
        // 检查用户名是否已存在
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new WebAdminException(WebAdminErrorCode.USER_ALREADY_EXISTS, request.getUsername());
        }
        
        User user = new User();
        BeanUtils.copyProperties(request, user);
        
        // 加密密码
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        
        userMapper.insert(user);
        
        // 分配角色
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            assignRoles(user.getId(), request.getRoleIds());
        }
        
        return getUserById(user.getId());
    }
    
    @Override
    @Transactional
    public UserRespDto updateUser(Long id, UserReqDto request) {
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            throw new WebAdminException(WebAdminErrorCode.USER_NOT_FOUND, id);
        }

        // 检查用户名是否被其他用户使用
        User userWithSameName = userMapper.findByUsername(request.getUsername());
        if (userWithSameName != null && !userWithSameName.getId().equals(id)) {
            throw new WebAdminException(WebAdminErrorCode.USERNAME_IN_USE, request.getUsername());
        }
        
        BeanUtils.copyProperties(request, existingUser, "id", "password", "createdAt");
        
        // 更新密码（如果提供）
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        existingUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(existingUser);
        
        // 更新角色
        if (request.getRoleIds() != null) {
            assignRoles(id, request.getRoleIds());
        }
        
        return getUserById(id);
    }
    
    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new WebAdminException(WebAdminErrorCode.USER_NOT_FOUND, id);
        }
        
        // 软删除
        user.setDeleted(1);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        // 删除用户角色关联
        userMapper.deleteUserRoles(id);
    }
    
    @Override
    public List<UserRespDto> getUserList() {
        List<User> users = userMapper.selectList(null);
        return users.stream()
                .map(user -> {
                    UserRespDto response = new UserRespDto();
                    BeanUtils.copyProperties(user, response);
                    response.setPermissions(getUserPermissionCodes(user.getId()));
                    return response;
                })
                .toList();
    }
    
    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        // 先删除现有角色
        userMapper.deleteUserRoles(userId);
        
        // 添加新角色
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRole.setCreatedAt(LocalDateTime.now());
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
    public UserRespDto updateProfile(Long userId, UpdateProfileReqDto request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new WebAdminException(WebAdminErrorCode.USER_NOT_FOUND, userId);
        }
        
        // 更新个人信息
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        // 返回更新后的用户信息
        return getUserById(userId);
    }
}