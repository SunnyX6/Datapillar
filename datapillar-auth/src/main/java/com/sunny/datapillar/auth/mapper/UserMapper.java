package com.sunny.datapillar.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.dto.login.response.RoleItem;
import com.sunny.datapillar.auth.entity.User;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for user persistence operations.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

  /** Query user by username. */
  User selectByUsername(@Param("username") String username);

  /** Query user by email. */
  User selectByEmail(@Param("email") String email);

  /** Query role list of the user. */
  List<RoleItem> selectRolesByUserId(
      @Param("tenantId") Long tenantId, @Param("userId") Long userId);

  /** Query accessible menu list for the user. */
  List<Map<String, Object>> selectMenusByUserId(
      @Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
