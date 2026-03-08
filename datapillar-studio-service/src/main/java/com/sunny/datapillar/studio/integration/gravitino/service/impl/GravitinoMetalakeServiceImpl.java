package com.sunny.datapillar.studio.integration.gravitino.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.studio.integration.gravitino.GravitinoMetadataClient;
import com.sunny.datapillar.studio.integration.gravitino.service.GravitinoMetalakeService;
import org.springframework.stereotype.Service;

@Service
public class GravitinoMetalakeServiceImpl implements GravitinoMetalakeService {

  private final GravitinoMetadataClient metadataClient;

  public GravitinoMetalakeServiceImpl(GravitinoMetadataClient metadataClient) {
    this.metadataClient = metadataClient;
  }

  @Override
  public boolean createMetalake(
      String metalakeName, String comment, JsonNode properties, String principalUsername) {
    return metadataClient.createMetalake(metalakeName, comment, properties, principalUsername);
  }

  @Override
  public boolean dropMetalake(String metalakeName, boolean force, String principalUsername) {
    return metadataClient.dropMetalake(metalakeName, force, principalUsername);
  }

  @Override
  public void setMetalakeOwner(String metalakeName, String ownerName, String principalUsername) {
    metadataClient.setMetalakeOwner(metalakeName, ownerName, principalUsername);
  }
}
