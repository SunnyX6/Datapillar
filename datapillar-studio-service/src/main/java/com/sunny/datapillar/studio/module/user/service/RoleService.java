package com.sunny.datapillar.studio.module.user.service;

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
import java.util.List;

/**
 * role service Provide role business capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface RoleService {

  /** According to roleIDQuery role details */
  RoleResponse getRoleById(Long id);

  /** Create a role */
  Long createRole(RoleCreateRequest dto);

  /** Update role */
  void updateRole(Long id, RoleUpdateRequest dto);

  /** Delete role */
  void deleteRole(Long id);

  /** Query role list */
  List<RoleResponse> getRoleList();

  /** According to userIDQuery role list */
  List<RoleResponse> getRolesByUserId(Long userId);

  /** Get role permissions */
  List<FeatureObjectPermissionItem> getRolePermissions(Long roleId, String scope);

  /** Update role permissions（Full coverage） */
  void updateRolePermissions(Long roleId, List<RoleFeatureAssignmentItem> permissions);

  /** Get role data privileges */
  List<RoleDataPrivilegeItem> getRoleDataPrivileges(Long roleId, String domain);

  /** Replace role data privileges */
  void replaceRoleDataPrivileges(
      Long roleId, String domain, List<RoleDataPrivilegeCommandItem> commands);

  /** Get role members */
  RoleMembersResponse getRoleMembers(Long roleId, Integer status);

  /** Remove role members in batches */
  void removeRoleMembers(Long roleId, List<Long> userIds);
}
