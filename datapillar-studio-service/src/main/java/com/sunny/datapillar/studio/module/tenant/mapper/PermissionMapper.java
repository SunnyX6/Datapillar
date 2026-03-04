package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * PermissionsMapper Responsible for permission data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

  /** According to roleIDQuery permission list */
  List<Permission> selectByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

  /** According to userIDQuery permission list */
  List<Permission> selectByUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

  /** Query permissions based on permission codes */
  Permission findByCode(@Param("code") String code);

  List<Permission> selectSystemPermissions();

  Permission selectSystemByCode(@Param("code") String code);
}
