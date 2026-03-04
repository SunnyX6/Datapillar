package com.sunny.datapillar.connector.gravitino.domain.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoSemanticClient;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import java.util.Map;

/** Semantic word root domain service. */
public class WordRootService {

  private final GravitinoSemanticClient semanticClient;

  public WordRootService(GravitinoSemanticClient semanticClient) {
    this.semanticClient = semanticClient;
  }

  public JsonNode request(
      String method,
      String path,
      Map<String, String> query,
      JsonNode body,
      ConnectorContext context) {
    return semanticClient.proxyRequest(method, path, query, body, context);
  }
}
