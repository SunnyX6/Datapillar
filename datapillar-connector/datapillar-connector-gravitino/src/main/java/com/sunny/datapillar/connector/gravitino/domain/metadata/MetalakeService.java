package com.sunny.datapillar.connector.gravitino.domain.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoMetadataClient;
import com.sunny.datapillar.connector.spi.ConnectorContext;

/** Metalake domain service. */
public class MetalakeService {

  private final GravitinoMetadataClient metadataClient;

  public MetalakeService(GravitinoMetadataClient metadataClient) {
    this.metadataClient = metadataClient;
  }

  public JsonNode listMetalakes(ConnectorContext context) {
    return metadataClient.listMetalakes(context);
  }

  public JsonNode loadMetalake(String metalakeName, ConnectorContext context) {
    return metadataClient.loadMetalake(metalakeName, context);
  }

  public JsonNode createMetalake(
      String metalakeName, String comment, JsonNode properties, ConnectorContext context) {
    return metadataClient.createMetalake(metalakeName, comment, properties, context);
  }
}
