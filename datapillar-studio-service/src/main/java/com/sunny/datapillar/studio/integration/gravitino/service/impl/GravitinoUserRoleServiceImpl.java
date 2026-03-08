package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.sunny.datapillar.studio.integration.gravitino.GravitinoAdminOpsClient;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserRoleService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GravitinoUserRoleServiceImpl implements GravitinoUserRoleService {

  private final GravitinoAdminOpsClient adminOpsClient;

  public GravitinoUserRoleServiceImpl(GravitinoAdminOpsClient adminOpsClient) {
    this.adminOpsClient = adminOpsClient;
  }

  @Override
  public void replaceUserRoles(String username, List<String> roleNames, String principalUsername) {
    adminOpsClient.replaceUserRoles(username, roleNames, principalUsername);
  }

  @Override
  public void revokeAllUserRoles(String username, String principalUsername) {
    adminOpsClient.revokeAllUserRoles(username, principalUsername);
  }
}
