package com.sunny.datapillar.studio.integration.gravitino.service;

public interface GravitinoRoleService {

  void createRole(String roleName, String principalUsername);

  void deleteRole(String roleName, String principalUsername);
}
