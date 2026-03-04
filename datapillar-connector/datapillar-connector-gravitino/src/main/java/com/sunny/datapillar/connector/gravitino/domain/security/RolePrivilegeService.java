package com.sunny.datapillar.connector.gravitino.domain.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoSecurityClient;
import com.sunny.datapillar.connector.spi.ConnectorContext;

/** Role data privilege domain service. */
public class RolePrivilegeService {

  private final GravitinoSecurityClient securityClient;

  public RolePrivilegeService(GravitinoSecurityClient securityClient) {
    this.securityClient = securityClient;
  }

  public JsonNode listRoleDataPrivileges(String roleName, String domain, ConnectorContext context) {
    return securityClient.listRoleDataPrivileges(roleName, domain, context);
  }

  public JsonNode syncRoleDataPrivileges(
      String roleName, String domain, JsonNode commands, ConnectorContext context) {
    return securityClient.syncRoleDataPrivileges(roleName, domain, commands, context);
  }
}
