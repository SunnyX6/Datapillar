package com.sunny.datapillar.admin.module.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.admin.module.user.entity.User;
import com.sunny.datapillar.admin.module.user.entity.UserRole;

/**
 * 用户 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户（包含角色信息）
     */
    User findByUsernameWithRoles(@Param("username") String username);

    /**
     * 根据用户名查询用户
     */
    User findByUsername(@Param("username") String username);

    /**
     * 根据用户ID查询用户角色代码
     */
    List<String> getUserRoleCodes(@Param("userId") Long userId);

    /**
     * 根据用户ID查询用户权限代码
     */
    List<String> getUserPermissionCodes(@Param("userId") Long userId);

    /**
     * 删除用户角色关联
     */
    void deleteUserRoles(@Param("userId") Long userId);

    /**
     * 插入用户角色关联
     */
    void insertUserRole(UserRole userRole);

    /**
     * 根据用户ID和Token签名查询用户
     */
    User selectByIdAndTokenSign(@Param("userId") Long userId, @Param("tokenSign") String tokenSign);
}
