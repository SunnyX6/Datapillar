package com.sunny.datapillar.studio.integration.gravitino.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface GravitinoMetalakeService {

  boolean createMetalake(
      String metalakeName, String comment, JsonNode properties, String principalUsername);

  boolean dropMetalake(String metalakeName, boolean force, String principalUsername);

  void setMetalakeOwner(String metalakeName, String ownerName, String principalUsername);
}
