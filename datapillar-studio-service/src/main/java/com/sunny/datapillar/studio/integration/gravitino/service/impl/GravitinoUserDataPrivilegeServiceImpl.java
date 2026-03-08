package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.sunny.datapillar.studio.dto.tenant.request.RoleDataPrivilegeCommandItem;
import com.sunny.datapillar.studio.dto.tenant.response.RoleDataPrivilegeItem;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoAdminOpsClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRolePrivilegeItemResponse;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDataPrivilegeMapper;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoUserDataPrivilegeService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GravitinoUserDataPrivilegeServiceImpl implements GravitinoUserDataPrivilegeService {

  private final GravitinoAdminOpsClient adminOpsClient;
  private final GravitinoDomainRoutingService domainRoutingService;

  public GravitinoUserDataPrivilegeServiceImpl(
      GravitinoAdminOpsClient adminOpsClient, GravitinoDomainRoutingService domainRoutingService) {
    this.adminOpsClient = adminOpsClient;
    this.domainRoutingService = domainRoutingService;
  }

  @Override
  public List<RoleDataPrivilegeItem> getUserDataPrivileges(
      Long userId, String username, String domain, String principalUsername) {
    String normalizedDomain = domainRoutingService.normalizeDomain(domain);
    return adminOpsClient
        .getUserOverridePrivileges(
            domainRoutingService.managedMetalake(), userId, username, principalUsername)
        .stream()
        .filter(item -> matchesDomain(normalizedDomain, item))
        .map(this::toRoleDataPrivilegeItem)
        .toList();
  }

  @Override
  public void replaceUserDataPrivileges(
      Long userId,
      String username,
      String domain,
      List<RoleDataPrivilegeCommandItem> commands,
      String principalUsername) {
    String normalizedDomain = domainRoutingService.normalizeDomain(domain);
    adminOpsClient.replaceUserOverridePrivileges(
        domainRoutingService.resolveMetalake(normalizedDomain),
        userId,
        username,
        normalizedDomain,
        GravitinoDataPrivilegeMapper.toGravitinoCommands(commands),
        principalUsername);
  }

  @Override
  public void clearUserDataPrivileges(
      Long userId, String username, String domain, String principalUsername) {
    String normalizedDomain = domainRoutingService.normalizeDomain(domain);
    adminOpsClient.clearUserOverridePrivileges(
        domainRoutingService.managedMetalake(),
        userId,
        username,
        normalizedDomain,
        principalUsername);
  }

  private boolean matchesDomain(String normalizedDomain, GravitinoRolePrivilegeItemResponse item) {
    return domainRoutingService.matchesDomain(
        normalizedDomain, item.getObjectType(), item.getObjectName());
  }

  private RoleDataPrivilegeItem toRoleDataPrivilegeItem(GravitinoRolePrivilegeItemResponse item) {
    return GravitinoDataPrivilegeMapper.toRoleDataPrivilegeItem(
        domainRoutingService.resolveDomainByObject(item.getObjectType(), item.getObjectName()),
        item);
  }
}
