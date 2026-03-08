package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.dto.tenant.request.RoleDataPrivilegeCommandItem;
import com.sunny.datapillar.studio.dto.tenant.response.RoleDataPrivilegeItem;
import java.util.List;

public interface GravitinoUserDataPrivilegeService {

  List<RoleDataPrivilegeItem> getUserDataPrivileges(
      Long userId, String username, String domain, String principalUsername);

  void replaceUserDataPrivileges(
      Long userId,
      String username,
      String domain,
      List<RoleDataPrivilegeCommandItem> commands,
      String principalUsername);

  void clearUserDataPrivileges(
      Long userId, String username, String domain, String principalUsername);
}
