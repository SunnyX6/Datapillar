package com.sunny.datapillar.auth.service.support;

import com.sunny.datapillar.auth.dto.login.response.RoleItem;
import com.sunny.datapillar.auth.mapper.UserMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reader for user access data required by token claims.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
@RequiredArgsConstructor
public class UserAccessReader {

  private final UserMapper userMapper;

  /** Read normalized role types required for access-token claims. */
  public List<String> loadRoleTypes(Long tenantId, Long userId) {
    if (tenantId == null || userId == null) {
      return new ArrayList<>();
    }
    List<RoleItem> roles = userMapper.selectRolesByUserId(tenantId, userId);
    if (roles == null || roles.isEmpty()) {
      return new ArrayList<>();
    }

    List<String> roleTypes = new ArrayList<>();
    for (RoleItem role : roles) {
      if (role == null || role.getType() == null || role.getType().isBlank()) {
        continue;
      }
      roleTypes.add(role.getType().trim().toUpperCase(Locale.ROOT));
    }
    return roleTypes;
  }
}
