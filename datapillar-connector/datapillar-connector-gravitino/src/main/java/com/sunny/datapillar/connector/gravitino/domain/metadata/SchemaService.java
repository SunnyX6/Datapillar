package com.sunny.datapillar.connector.gravitino.domain.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoMetadataClient;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import java.util.Map;

/** Schema domain service. */
public class SchemaService {

  private final GravitinoMetadataClient metadataClient;

  public SchemaService(GravitinoMetadataClient metadataClient) {
    this.metadataClient = metadataClient;
  }

  public JsonNode request(
      String method,
      String path,
      Map<String, String> query,
      JsonNode body,
      ConnectorContext context) {
    return metadataClient.proxyRequest(method, path, query, body, context);
  }
}
