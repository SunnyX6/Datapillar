package com.sunny.datapillar.studio.integration.gravitino.service;

import com.sunny.datapillar.studio.dto.tenant.request.RoleDataPrivilegeCommandItem;
import com.sunny.datapillar.studio.dto.tenant.response.RoleDataPrivilegeItem;
import java.util.List;

public interface GravitinoRolePrivilegeService {

  List<RoleDataPrivilegeItem> getRoleDataPrivileges(
      String roleName, String domain, String principalUsername);

  void replaceRoleDataPrivileges(
      String roleName,
      String domain,
      List<RoleDataPrivilegeCommandItem> commands,
      String principalUsername);
}
