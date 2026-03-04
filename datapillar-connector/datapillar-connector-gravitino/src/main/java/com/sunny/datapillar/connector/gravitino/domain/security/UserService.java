package com.sunny.datapillar.connector.gravitino.domain.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoSecurityClient;
import com.sunny.datapillar.connector.spi.ConnectorContext;

/** Security user domain service. */
public class UserService {

  private final GravitinoSecurityClient securityClient;

  public UserService(GravitinoSecurityClient securityClient) {
    this.securityClient = securityClient;
  }

  public JsonNode syncUser(String username, ConnectorContext context) {
    return securityClient.syncUser(username, context);
  }
}
