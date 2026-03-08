package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.sunny.datapillar.studio.integration.gravitino.GravitinoAdminOpsClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoOwnerResponse;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoOwnerService;
import org.springframework.stereotype.Service;

@Service
public class GravitinoOwnerServiceImpl implements GravitinoOwnerService {

  private final GravitinoAdminOpsClient adminOpsClient;
  private final GravitinoDomainRoutingService domainRoutingService;

  public GravitinoOwnerServiceImpl(
      GravitinoAdminOpsClient adminOpsClient, GravitinoDomainRoutingService domainRoutingService) {
    this.adminOpsClient = adminOpsClient;
    this.domainRoutingService = domainRoutingService;
  }

  @Override
  public GravitinoOwnerResponse getOwner(String domain, String objectType, String fullName) {
    return adminOpsClient.getOwner(
        domainRoutingService.resolveMetalake(domain), objectType, fullName, null);
  }

  @Override
  public GravitinoOwnerResponse getOwner(
      String metalake, String objectType, String fullName, String principalUsername) {
    return adminOpsClient.getOwner(metalake, objectType, fullName, principalUsername);
  }

  @Override
  public void setOwner(
      String metalake,
      String objectType,
      String fullName,
      String ownerName,
      String ownerType,
      String principalUsername) {
    adminOpsClient.setOwner(
        metalake, objectType, fullName, ownerName, ownerType, principalUsername);
  }
}
