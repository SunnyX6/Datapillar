package com.sunny.datapillar.studio.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
import com.sunny.datapillar.studio.module.user.entity.Role;
import com.sunny.datapillar.studio.module.user.entity.RolePermission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * roleMapper Responsible for role data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

  /** According to userIDQuery role list */
  List<Role> findByUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

  /** Query roles based on role name */
  Role findByName(@Param("tenantId") Long tenantId, @Param("name") String name);

  /** According to roleIDQuery and lock */
  Role selectByIdForUpdate(@Param("id") Long id);

  /** Delete role permission association */
  void deleteRolePermissions(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

  /** According to roleIDDelete user role association */
  void deleteUserRolesByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

  /** Insert role permission association */
  void insertRolePermission(RolePermission rolePermission);

  /** Query tenant role list（Including number of members） */
  List<RoleResponse> selectRoleListWithMemberCount(@Param("tenantId") Long tenantId);

  /** Count the number of members under a role */
  long countUsersByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

  /** Query the maximum sorting value of a tenant */
  Integer selectMaxSortByTenant(@Param("tenantId") Long tenantId);

  /** Query role member details */
  List<RoleMemberItem> selectRoleMembers(
      @Param("tenantId") Long tenantId,
      @Param("roleId") Long roleId,
      @Param("status") Integer status);

  /** Delete user role associations in batches by role */
  void deleteRoleMembersByUserIds(
      @Param("tenantId") Long tenantId,
      @Param("roleId") Long roleId,
      @Param("userIds") List<Long> userIds);
}
