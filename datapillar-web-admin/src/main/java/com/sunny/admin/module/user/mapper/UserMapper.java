package com.sunny.admin.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.admin.module.user.entity.User;
import com.sunny.admin.module.user.entity.UserRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户Mapper接口
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 根据用户名查询用户（包含角色信息）
     * 
     * @param username 用户名
     * @return 用户信息
     */
    @Select("SELECT u.*, GROUP_CONCAT(r.code) as role_codes " +
            "FROM users u " +
            "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
            "LEFT JOIN roles r ON ur.role_id = r.id " +
            "WHERE u.username = #{username} AND u.deleted = 0 " +
            "GROUP BY u.id")
    User findByUsernameWithRoles(@Param("username") String username);
    
    /**
     * 根据用户名查询用户
     * 
     * @param username 用户名
     * @return 用户信息
     */
    @Select("SELECT * FROM users WHERE username = #{username} AND deleted = 0")
    User findByUsername(@Param("username") String username);
    
    /**
     * 根据用户ID查询用户角色
     * 
     * @param userId 用户ID
     * @return 角色代码列表
     */
    @Select("SELECT r.code FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> getUserRoleCodes(@Param("userId") Long userId);
    
    /**
     * 根据用户ID查询用户权限
     * 
     * @param userId 用户ID
     * @return 权限代码列表
     */
    @Select("SELECT DISTINCT p.code FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.id = rp.permission_id " +
            "INNER JOIN user_roles ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> getUserPermissionCodes(@Param("userId") Long userId);
    
    /**
     * 删除用户角色关联
     * 
     * @param userId 用户ID
     */
    @Delete("DELETE FROM user_roles WHERE user_id = #{userId}")
    void deleteUserRoles(@Param("userId") Long userId);
    
    /**
     * 插入用户角色关联
     *
     * @param userRole 用户角色关联
     */
    @Insert("INSERT INTO user_roles (user_id, role_id, created_at) VALUES (#{userId}, #{roleId}, #{createdAt})")
    void insertUserRole(UserRole userRole);

    /**
     * 根据用户ID和Token签名查询用户（用于SSO验证）
     *
     * @param userId 用户ID
     * @param tokenSign Token签名
     * @return 用户信息，如果Token签名不匹配或用户不存在则返回null
     */
    @Select("SELECT * FROM users WHERE id = #{userId} AND token_sign = #{tokenSign} AND deleted = 0")
    User selectByIdAndTokenSign(@Param("userId") Long userId, @Param("tokenSign") String tokenSign);
}