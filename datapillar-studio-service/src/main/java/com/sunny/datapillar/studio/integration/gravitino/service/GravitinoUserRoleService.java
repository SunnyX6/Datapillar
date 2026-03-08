package com.sunny.datapillar.studio.integration.gravitino.service;

import java.util.List;

public interface GravitinoUserRoleService {

  void replaceUserRoles(String username, List<String> roleNames, String principalUsername);

  void revokeAllUserRoles(String username, String principalUsername);
}
