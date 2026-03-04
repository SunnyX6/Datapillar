package com.sunny.datapillar.connector.gravitino.transport.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.connector.gravitino.error.GravitinoErrorMapper;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.gravitino.client.ConnectorClientAccessor;
import org.apache.gravitino.client.ErrorHandlers;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.client.RESTClient;
import org.apache.gravitino.dto.responses.ErrorResponse;

/** Gravitino semantic transport client. */
public class GravitinoSemanticClient {

  private final GravitinoSdkClientFactory clientFactory;
  private final ObjectMapper objectMapper;
  private final GravitinoErrorMapper errorMapper;

  public GravitinoSemanticClient(
      GravitinoSdkClientFactory clientFactory,
      ObjectMapper objectMapper,
      GravitinoErrorMapper errorMapper) {
    this.clientFactory = clientFactory;
    this.objectMapper = objectMapper;
    this.errorMapper = errorMapper;
  }

  public JsonNode proxyRequest(
      String method,
      String path,
      Map<String, String> query,
      JsonNode body,
      ConnectorContext context) {
    try (GravitinoClient client = clientFactory.createSemanticClient(context)) {
      RESTClient restClient = ConnectorClientAccessor.restClient(client);
      String targetPath = buildSemanticPath(path);
      Consumer<ErrorResponse> errorHandler = ErrorHandlers.restErrorHandler();
      JsonPayloadResponse response =
          switch (normalizeMethod(method)) {
            case "GET" ->
                restClient.get(
                    targetPath,
                    nullSafeQuery(query),
                    JsonPayloadResponse.class,
                    Map.of(),
                    errorHandler);
            case "POST" ->
                restClient.post(
                    targetPath,
                    new JsonPayloadRequest(nullSafeBody(body)),
                    JsonPayloadResponse.class,
                    Map.of(),
                    errorHandler);
            case "PUT" ->
                restClient.put(
                    targetPath,
                    new JsonPayloadRequest(nullSafeBody(body)),
                    JsonPayloadResponse.class,
                    Map.of(),
                    errorHandler);
            case "PATCH" ->
                restClient.patch(
                    targetPath,
                    new JsonPayloadRequest(nullSafeBody(body)),
                    JsonPayloadResponse.class,
                    Map.of(),
                    errorHandler);
            case "DELETE" ->
                restClient.delete(
                    targetPath,
                    nullSafeQuery(query),
                    JsonPayloadResponse.class,
                    Map.of(),
                    errorHandler);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
          };
      return response.toJsonNode(objectMapper);
    } catch (Exception exception) {
      throw errorMapper.map(exception);
    }
  }

  private String buildSemanticPath(String path) {
    String normalizedPath = path == null || path.isBlank() ? "" : path.trim();
    if (!normalizedPath.startsWith("/")) {
      normalizedPath = "/" + normalizedPath;
    }

    String metalakeBase = "api/metalakes/" + clientFactory.config().semanticMetalake();
    if (normalizedPath.startsWith("/objects") || normalizedPath.startsWith("/tags")) {
      return metalakeBase + normalizedPath;
    }
    return metalakeBase
        + "/catalogs/"
        + clientFactory.config().semanticCatalog()
        + "/schemas/"
        + clientFactory.config().semanticSchema()
        + normalizedPath;
  }

  private JsonNode nullSafeBody(JsonNode body) {
    return body == null || body.isNull() ? objectMapper.createObjectNode() : body;
  }

  private Map<String, String> nullSafeQuery(Map<String, String> query) {
    return query == null ? Map.of() : query;
  }

  private String normalizeMethod(String method) {
    if (method == null || method.isBlank()) {
      return "GET";
    }
    return method.trim().toUpperCase();
  }
}
