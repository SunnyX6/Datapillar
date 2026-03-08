package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.sunny.datapillar.studio.integration.gravitino.GravitinoAdminOpsClient;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GravitinoUserServiceImpl implements GravitinoUserService {

  private final GravitinoAdminOpsClient adminOpsClient;

  public GravitinoUserServiceImpl(GravitinoAdminOpsClient adminOpsClient) {
    this.adminOpsClient = adminOpsClient;
  }

  @Override
  public List<String> createUser(String username, Long externalUserId, String principalUsername) {
    return adminOpsClient.createUser(username, externalUserId, principalUsername);
  }

  @Override
  public void deleteUser(String username, String principalUsername) {
    adminOpsClient.deleteUser(username, principalUsername);
  }

  @Override
  public void deleteUser(String metalake, String username, String principalUsername) {
    adminOpsClient.deleteUser(metalake, username, principalUsername);
  }
}
