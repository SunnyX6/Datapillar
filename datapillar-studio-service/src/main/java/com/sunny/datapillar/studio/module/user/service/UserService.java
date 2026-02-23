package com.sunny.datapillar.studio.module.user.service;

import java.util.List;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.user.dto.UserDto;
import com.sunny.datapillar.studio.module.user.entity.User;

/**
 * 用户服务
 * 提供用户业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface UserService {

    /**
     * 根据用户名查询用户
     */
    User findByUsername(String username);

    /**
     * 根据用户ID查询用户详情
     */
    UserDto.Response getUserById(Long id);

    /**
     * 创建用户
     */
    Long createUser(UserDto.Create dto);

    /**
     * 更新用户
     */
    void updateUser(Long id, UserDto.Update dto);

    /**
     * 删除用户
     */
    void deleteUser(Long id);

    /**
     * 查询用户列表
     */
    List<UserDto.Response> getUserList();

    /**
     * 分页查询用户列表
     */
    List<User> listUsers(Integer status);

    /**
     * 更新当前租户成员状态
     */
    void updateTenantMemberStatus(Long userId, Integer status);

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
     * 获取用户权限
     */
    List<FeatureObjectDto.ObjectPermission> getUserPermissions(Long userId);

    /**
     * 更新当前用户个人信息
     */
    void updateProfile(Long userId, UserDto.UpdateProfile dto);
}
