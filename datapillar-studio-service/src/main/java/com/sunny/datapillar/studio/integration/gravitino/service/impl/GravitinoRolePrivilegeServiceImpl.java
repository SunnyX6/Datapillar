package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.sunny.datapillar.studio.dto.tenant.request.RoleDataPrivilegeCommandItem;
import com.sunny.datapillar.studio.dto.tenant.response.RoleDataPrivilegeItem;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoAdminOpsClient;
import com.sunny.datapillar.studio.integration.gravitino.model.GravitinoRolePrivilegeItemResponse;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDataPrivilegeMapper;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoDomainRoutingService;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoRolePrivilegeService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GravitinoRolePrivilegeServiceImpl implements GravitinoRolePrivilegeService {

  private final GravitinoAdminOpsClient adminOpsClient;
  private final GravitinoDomainRoutingService domainRoutingService;

  public GravitinoRolePrivilegeServiceImpl(
      GravitinoAdminOpsClient adminOpsClient, GravitinoDomainRoutingService domainRoutingService) {
    this.adminOpsClient = adminOpsClient;
    this.domainRoutingService = domainRoutingService;
  }

  @Override
  public List<RoleDataPrivilegeItem> getRoleDataPrivileges(
      String roleName, String domain, String principalUsername) {
    String normalizedDomain = domainRoutingService.normalizeDomain(domain);
    return adminOpsClient
        .getRolePrivileges(domainRoutingService.managedMetalake(), roleName, principalUsername)
        .stream()
        .filter(item -> matchesDomain(normalizedDomain, item))
        .map(this::toRoleDataPrivilegeItem)
        .toList();
  }

  @Override
  public void replaceRoleDataPrivileges(
      String roleName,
      String domain,
      List<RoleDataPrivilegeCommandItem> commands,
      String principalUsername) {
    String normalizedDomain = domainRoutingService.normalizeDomain(domain);
    adminOpsClient.replaceRolePrivileges(
        domainRoutingService.resolveMetalake(normalizedDomain),
        roleName,
        normalizedDomain,
        GravitinoDataPrivilegeMapper.toGravitinoCommands(commands),
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
