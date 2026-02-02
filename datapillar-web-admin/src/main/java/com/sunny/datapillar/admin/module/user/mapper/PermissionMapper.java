package com.sunny.datapillar.admin.module.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.admin.module.user.entity.Permission;

/**
 * 权限 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 根据角色ID查询权限列表
     */
    List<Permission> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据用户ID查询权限列表
     */
    List<Permission> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据权限代码查询权限
     */
    Permission findByCode(@Param("code") String code);
}
