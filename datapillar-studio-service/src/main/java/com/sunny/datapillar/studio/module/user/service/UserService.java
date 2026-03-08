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
import com.sunny.datapillar.studio.module.user.entity.User;
import java.util.List;

/**
 * User service Provide users with business capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface UserService {

  /** Query users based on username */
  User findByUsername(String username);

  /** According to userIDQuery user details */
  UserResponse getUserById(Long id);

  /** Create user */
  Long createUser(UserCreateRequest dto);

  default Long createUser(UserCreateRequest dto, boolean provisionGravitino) {
    return createUser(dto);
  }

  default Long createUser(
      UserCreateRequest dto, boolean provisionGravitino, String gravitinoCreatorUsername) {
    return createUser(dto, provisionGravitino);
  }

  /** Update user */
  void updateUser(Long id, UserUpdateRequest dto);

  /** Delete user */
  void deleteUser(Long id);

  /** Query user list */
  List<UserResponse> getUserList();

  /** Query user list by page */
  List<User> listUsers(Integer status);

  /** Update current tenant member status */
  void updateTenantMemberStatus(Long userId, Integer status);

  /** Assign roles to users */
  void assignRoles(Long userId, List<Long> roleIds);

  /** Get a list of user role codes */
  List<String> getUserRoleCodes(Long userId);

  /** Get a list of user permission codes */
  List<String> getUserPermissionCodes(Long userId);

  /** Get user permissions */
  List<FeatureObjectPermissionItem> getUserPermissions(Long userId);

  /** Get user data privileges */
  List<RoleDataPrivilegeItem> getUserDataPrivileges(Long userId, String domain);

  /** Replace user data privileges */
  void replaceUserDataPrivileges(
      Long userId, String domain, List<RoleDataPrivilegeCommandItem> commands);

  /** Clear user data privileges */
  void clearUserDataPrivileges(Long userId, String domain);

  /** Update current user personal information */
  void updateProfile(Long userId, UserProfileUpdateRequest dto);
}
