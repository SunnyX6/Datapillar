package com.sunny.datapillar.admin.module.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sunny.datapillar.admin.module.user.dto.PermissionObjectDto;
import com.sunny.datapillar.admin.module.user.dto.PermissionObjectDto.RoleSource;

/**
 * 权限对象 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface PermissionObjectMapper {

    List<PermissionObjectDto.ObjectPermission> selectPermissionObjectsAll();

    List<PermissionObjectDto.ObjectPermission> selectRoleObjectPermissionsAll(@Param("roleId") Long roleId);

    List<PermissionObjectDto.ObjectPermission> selectRoleObjectPermissionsAssigned(@Param("roleId") Long roleId);

    List<RoleSource> selectUserRoleSources(@Param("userId") Long userId);

    List<PermissionObjectDto.Assignment> selectUserOverridePermissions(@Param("userId") Long userId);
}
