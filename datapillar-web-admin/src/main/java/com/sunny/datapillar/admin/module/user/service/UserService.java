package com.sunny.datapillar.admin.module.user.service;

import java.util.List;

import com.sunny.datapillar.admin.module.user.dto.PermissionObjectDto;
import com.sunny.datapillar.admin.module.user.dto.UserDto;
import com.sunny.datapillar.admin.module.user.entity.User;

/**
 * 用户服务接口
 *
 * @author sunny
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
    List<PermissionObjectDto.ObjectPermission> getUserPermissions(Long userId);

    /**
     * 更新用户权限（全量覆盖）
     */
    void updateUserPermissions(Long userId, List<PermissionObjectDto.Assignment> permissions);

    /**
     * 更新当前用户个人信息
     */
    void updateProfile(Long userId, UserDto.UpdateProfile dto);
}
