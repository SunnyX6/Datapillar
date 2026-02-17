package com.sunny.datapillar.auth.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.User;

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
     * 根据用户名查询用户
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 查询用户的角色列表
     */
    List<com.sunny.datapillar.auth.dto.AuthDto.RoleInfo> selectRolesByUserId(@Param("tenantId") Long tenantId,
                                                                             @Param("userId") Long userId);

    /**
     * 查询用户可访问的菜单列表
     */
    List<Map<String, Object>> selectMenusByUserId(@Param("tenantId") Long tenantId,
                                                  @Param("userId") Long userId);
}
