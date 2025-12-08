package com.sunny.admin.module.user.service;

import com.sunny.admin.module.user.dto.UserReqDto;
import com.sunny.admin.module.user.dto.UserRespDto;
import com.sunny.admin.module.user.dto.UpdateProfileReqDto;
import com.sunny.admin.module.user.entity.User;

import java.util.List;

/**
 * 用户服务接口
 * 
 * @author sunny
 * @since 2024-01-01
 */
public interface UserService {
    
    /**
     * 根据用户名查询用户
     */
    User findByUsername(String username);
    
    /**
     * 根据用户ID查询用户详情
     */
    UserRespDto getUserById(Long id);
    
    /**
     * 创建用户
     */
    UserRespDto createUser(UserReqDto request);
    
    /**
     * 更新用户
     */
    UserRespDto updateUser(Long id, UserReqDto request);
    
    /**
     * 删除用户
     */
    void deleteUser(Long id);
    
    /**
     * 查询用户列表
     */
    List<UserRespDto> getUserList();
    
    /**
     * 为用户分配角色
     */
    void assignRoles(Long userId, List<Long> roleIds);
    
    /**
     * 获取用户角色代码列表
     */
    List<String> getUserRoleCodes(Long userId);
    
    /**
     * 获取用户权限代码列表
     */
    List<String> getUserPermissionCodes(Long userId);

    /**
     * 更新当前用户个人信息
     */
    UserRespDto updateProfile(Long userId, UpdateProfileReqDto request);
}