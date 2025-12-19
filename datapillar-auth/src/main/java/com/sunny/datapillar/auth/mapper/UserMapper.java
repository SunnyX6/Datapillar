package com.sunny.datapillar.auth.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.User;

/**
 * 用户 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 更新用户的Token签名和过期时间
     */
    int updateTokenSign(@Param("userId") Long userId, @Param("tokenSign") String tokenSign, @Param("expireTime") LocalDateTime expireTime);

    /**
     * 根据用户ID和Token签名查询用户
     */
    User selectByIdAndTokenSign(@Param("userId") Long userId, @Param("tokenSign") String tokenSign);

    /**
     * 清空用户的Token签名
     */
    int clearTokenSign(@Param("userId") Long userId);

    /**
     * 查询用户的角色代码列表
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的权限代码列表
     */
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户可访问的菜单列表
     */
    List<Map<String, Object>> selectMenusByUserId(@Param("userId") Long userId);
}
