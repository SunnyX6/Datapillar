package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.sunny.datapillar.studio.integration.gravitino.GravitinoAdminOpsClient;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoRoleService;
import org.springframework.stereotype.Service;

@Service
public class GravitinoRoleServiceImpl implements GravitinoRoleService {

  private final GravitinoAdminOpsClient adminOpsClient;

  public GravitinoRoleServiceImpl(GravitinoAdminOpsClient adminOpsClient) {
    this.adminOpsClient = adminOpsClient;
  }

  @Override
  public void createRole(String roleName, String principalUsername) {
    adminOpsClient.createRole(roleName, principalUsername);
  }

  @Override
  public void deleteRole(String roleName, String principalUsername) {
    adminOpsClient.deleteRole(roleName, principalUsername);
  }
}
