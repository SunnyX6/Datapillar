package com.sunny.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT id, username, password as passwordHash, email, status, token_sign as tokenSign, token_expire_time as tokenExpireTime, created_at as createdAt, updated_at as updatedAt FROM users WHERE username = #{username}")
    User selectByUsername(String username);

    @Select("SELECT id, username, password as passwordHash, email, status, token_sign as tokenSign, token_expire_time as tokenExpireTime, created_at as createdAt, updated_at as updatedAt FROM users WHERE email = #{email}")
    User selectByEmail(String email);

    /**
     * 更新用户的Token签名和过期时间（用于SSO）
     */
    @Update("UPDATE users SET token_sign = #{tokenSign}, token_expire_time = #{expireTime} WHERE id = #{userId}")
    int updateTokenSign(@Param("userId") Long userId,
                        @Param("tokenSign") String tokenSign,
                        @Param("expireTime") LocalDateTime expireTime);

    /**
     * 根据用户ID和Token签名查询用户（用于SSO验证）
     */
    @Select("SELECT id, username, password as passwordHash, email, status, token_sign as tokenSign, token_expire_time as tokenExpireTime, created_at as createdAt, updated_at as updatedAt FROM users WHERE id = #{userId} AND token_sign = #{tokenSign}")
    User selectByIdAndTokenSign(@Param("userId") Long userId, @Param("tokenSign") String tokenSign);

    /**
     * 清空用户的Token签名（用于登出）
     */
    @Update("UPDATE users SET token_sign = NULL, token_expire_time = NULL WHERE id = #{userId}")
    int clearTokenSign(@Param("userId") Long userId);

    /**
     * 查询用户的角色代码列表
     */
    @Select("SELECT r.code FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    java.util.List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的权限代码列表
     */
    @Select("SELECT DISTINCT p.code FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.id = rp.permission_id " +
            "INNER JOIN user_roles ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    java.util.List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户可访问的菜单列表（树形结构）
     */
    @Select("SELECT DISTINCT m.id, m.name, m.path, m.icon, m.permission_code, m.parent_id, m.sort " +
            "FROM menus m " +
            "LEFT JOIN menu_roles mr ON m.id = mr.menu_id " +
            "LEFT JOIN user_roles ur ON mr.role_id = ur.role_id " +
            "WHERE (ur.user_id = #{userId} OR m.permission_code IS NULL) " +
            "AND m.visible = 1 " +
            "ORDER BY m.sort ASC")
    java.util.List<java.util.Map<String, Object>> selectMenusByUserId(@Param("userId") Long userId);
}
