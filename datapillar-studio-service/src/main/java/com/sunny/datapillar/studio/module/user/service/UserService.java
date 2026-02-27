package com.sunny.datapillar.studio.module.user.service;

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
import java.util.List;

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
    UserResponse getUserById(Long id);

    /**
     * 创建用户
     */
    Long createUser(UserCreateRequest dto);

    /**
     * 更新用户
     */
    void updateUser(Long id, UserUpdateRequest dto);

    /**
     * 删除用户
     */
    void deleteUser(Long id);

    /**
     * 查询用户列表
     */
    List<UserResponse> getUserList();

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
    List<FeatureObjectPermissionItem> getUserPermissions(Long userId);

    /**
     * 更新当前用户个人信息
     */
    void updateProfile(Long userId, UserProfileUpdateRequest dto);
}
