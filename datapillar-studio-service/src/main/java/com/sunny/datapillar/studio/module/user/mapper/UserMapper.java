package com.sunny.datapillar.studio.module.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.entity.UserRole;

/**
 * 用户Mapper
 * 负责用户数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户（包含角色信息）
     */
    User findByUsernameWithRoles(@Param("tenantId") Long tenantId, @Param("username") String username);

    /**
     * 根据用户名查询用户
     */
    User findByUsername(@Param("tenantId") Long tenantId, @Param("username") String username);

    /**
     * 全局查询用户（不做租户过滤）
     */
    User selectByUsernameGlobal(@Param("username") String username);

    /**
     * 根据用户ID查询用户角色代码
     */
    List<String> getUserRoleCodes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 根据用户ID查询用户权限代码
     */
    List<String> getUserPermissionCodes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 删除用户角色关联
     */
    void deleteUserRoles(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 插入用户角色关联
     */
    void insertUserRole(UserRole userRole);

    /**
     * 按租户查询用户列表
     */
    List<User> selectUsersByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 按租户和状态查询用户列表
     */
    List<User> selectUsersByTenantIdAndStatus(@Param("tenantId") Long tenantId,
                                              @Param("status") Integer status);

    /**
     * 按租户查询用户详情
     */
    User selectByIdAndTenantId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
