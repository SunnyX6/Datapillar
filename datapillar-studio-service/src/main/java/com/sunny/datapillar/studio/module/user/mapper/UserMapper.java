package com.sunny.datapillar.studio.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.entity.UserRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * UserMapper Responsible for user data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

  /** Query users based on username（Contains role information） */
  User findByUsernameWithRoles(
      @Param("tenantId") Long tenantId, @Param("username") String username);

  /** Query users based on username */
  User findByUsername(@Param("tenantId") Long tenantId, @Param("username") String username);

  /** Query users globally（No tenant filtering） */
  User selectByUsernameGlobal(@Param("username") String username);

  /** According to userIDQuery user role code */
  List<String> getUserRoleCodes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

  /** According to userIDQuery user permission code */
  List<String> getUserPermissionCodes(
      @Param("tenantId") Long tenantId, @Param("userId") Long userId);

  /** Delete user role association */
  void deleteUserRoles(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

  /** Insert user role association */
  void insertUserRole(UserRole userRole);

  /** Query user list by tenant */
  List<User> selectUsersByTenantId(@Param("tenantId") Long tenantId);

  /** Query user list by tenant and status */
  List<User> selectUsersByTenantIdAndStatus(
      @Param("tenantId") Long tenantId, @Param("status") Integer status);

  /** Query user details by tenant */
  User selectByIdAndTenantId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

  /**
   * Query users of a specified level and above in a tenantID（level The smaller the value, the
   * higher the authority.）
   */
  List<Long> selectUserIdsByMaxLevel(
      @Param("tenantId") Long tenantId,
      @Param("userIds") List<Long> userIds,
      @Param("maxLevel") Integer maxLevel);
}
