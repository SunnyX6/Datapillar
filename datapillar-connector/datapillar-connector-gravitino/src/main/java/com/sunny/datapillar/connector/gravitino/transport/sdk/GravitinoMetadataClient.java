package com.sunny.datapillar.connector.gravitino.transport.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.connector.gravitino.error.GravitinoErrorMapper;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.gravitino.client.ConnectorClientAccessor;
import org.apache.gravitino.client.ErrorHandlers;
import org.apache.gravitino.client.GravitinoAdminClient;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.client.RESTClient;
import org.apache.gravitino.dto.responses.ErrorResponse;
import org.apache.gravitino.exceptions.MetalakeAlreadyExistsException;

/** Gravitino metadata transport client. */
public class GravitinoMetadataClient {

  private final GravitinoSdkClientFactory clientFactory;
  private final ObjectMapper objectMapper;
  private final GravitinoErrorMapper errorMapper;

  public GravitinoMetadataClient(
      GravitinoSdkClientFactory clientFactory,
      ObjectMapper objectMapper,
      GravitinoErrorMapper errorMapper) {
    this.clientFactory = clientFactory;
    this.objectMapper = objectMapper;
    this.errorMapper = errorMapper;
  }

  public JsonNode listMetalakes(ConnectorContext context) {
    try (GravitinoAdminClient client = clientFactory.createAdminClient(context)) {
      var response = objectMapper.createObjectNode();
      response.put("code", 0);
      var metalakes = objectMapper.createArrayNode();
      for (var metalake : client.listMetalakes()) {
        var item = objectMapper.createObjectNode();
        item.put("name", metalake.name());
        item.put("comment", metalake.comment());
        item.set("properties", objectMapper.valueToTree(metalake.properties()));
        metalakes.add(item);
      }
      response.set("metalakes", metalakes);
      return response;
    } catch (Exception exception) {
      throw errorMapper.map(exception);
    }
  }

  public JsonNode loadMetalake(String metalakeName, ConnectorContext context) {
    try (GravitinoAdminClient client = clientFactory.createAdminClient(context)) {
      var metalake = client.loadMetalake(metalakeName);
      var response = objectMapper.createObjectNode();
      response.put("code", 0);
      var item = objectMapper.createObjectNode();
      item.put("name", metalake.name());
      item.put("comment", metalake.comment());
      item.set("properties", objectMapper.valueToTree(metalake.properties()));
      response.set("metalake", item);
      return response;
    } catch (Exception exception) {
      throw errorMapper.map(exception);
    }
  }

  public JsonNode createMetalake(
      String metalakeName, String comment, JsonNode properties, ConnectorContext context) {
    Map<String, String> propertyMap =
        properties == null || properties.isNull()
            ? Map.of()
            : objectMapper.convertValue(
                properties,
                objectMapper
                    .getTypeFactory()
                    .constructMapType(Map.class, String.class, String.class));
    try (GravitinoAdminClient client = clientFactory.createAdminClient(context)) {
      try {
        var metalake = client.createMetalake(metalakeName, comment, propertyMap);
        var response = objectMapper.createObjectNode();
        response.put("code", 0);
        var item = objectMapper.createObjectNode();
        item.put("name", metalake.name());
        item.put("comment", metalake.comment());
        item.set("properties", objectMapper.valueToTree(metalake.properties()));
        response.set("metalake", item);
        return response;
      } catch (MetalakeAlreadyExistsException ignored) {
        var response = objectMapper.createObjectNode();
        response.put("code", 0);
        response.put("idempotent", true);
        return response;
      }
    } catch (Exception exception) {
      throw errorMapper.map(exception);
    }
  }

  public JsonNode proxyRequest(
      String method,
      String path,
      Map<String, String> query,
      JsonNode body,
      ConnectorContext context) {
    try (GravitinoClient client = clientFactory.createMetadataClient(context)) {
      RESTClient restClient = ConnectorClientAccessor.restClient(client);
      String targetPath = buildMetadataPath(path);
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

  private String buildMetadataPath(String path) {
    String normalizedPath = path == null || path.isBlank() ? "" : path.trim();
    if (!normalizedPath.startsWith("/")) {
      normalizedPath = "/" + normalizedPath;
    }
    return "api/metalakes/" + clientFactory.config().metadataMetalake() + normalizedPath;
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
