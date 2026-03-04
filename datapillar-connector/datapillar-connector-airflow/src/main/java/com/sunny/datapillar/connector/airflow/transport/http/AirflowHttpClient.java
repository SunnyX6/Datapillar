package com.sunny.datapillar.connector.airflow.transport.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.connector.airflow.config.AirflowConnectorConfig;
import com.sunny.datapillar.connector.airflow.error.AirflowErrorMapper;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ErrorType;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** HTTP transport for datapillar-airflow-plugin. */
public class AirflowHttpClient {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final AirflowConnectorConfig config;
  private final ObjectMapper objectMapper;
  private final AirflowErrorMapper errorMapper;
  private final OkHttpClient httpClient;

  private volatile String cachedToken;
  private volatile Instant tokenExpiry;

  public AirflowHttpClient(
      AirflowConnectorConfig config, ObjectMapper objectMapper, AirflowErrorMapper errorMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.errorMapper = errorMapper;
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .build();
  }

  public JsonNode get(String path, Map<String, String> query, ConnectorContext context) {
    return execute("GET", path, null, query, context);
  }

  public JsonNode post(
      String path, JsonNode body, Map<String, String> query, ConnectorContext context) {
    return execute("POST", path, body, query, context);
  }

  public JsonNode patch(
      String path, JsonNode body, Map<String, String> query, ConnectorContext context) {
    return execute("PATCH", path, body, query, context);
  }

  public JsonNode delete(String path, Map<String, String> query, ConnectorContext context) {
    return execute("DELETE", path, null, query, context);
  }

  private JsonNode execute(
      String method,
      String path,
      JsonNode body,
      Map<String, String> query,
      ConnectorContext context) {
    try {
      Request.Builder builder = new Request.Builder().url(buildUrl(path, query));
      addAuthHeaders(builder, context);

      switch (method) {
        case "GET" -> builder.get();
        case "DELETE" -> builder.delete();
        case "POST" -> builder.post(toRequestBody(body));
        case "PATCH" -> builder.patch(toRequestBody(body));
        default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
      }

      try (Response response = httpClient.newCall(builder.build()).execute()) {
        String payload = response.body() == null ? "" : response.body().string();
        if (!response.isSuccessful()) {
          throw errorMapper.fromHttpStatus(response.code(), payload);
        }
        if (payload == null || payload.isBlank()) {
          return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(payload);
      }
    } catch (ConnectorException connectorException) {
      throw connectorException;
    } catch (IOException ioException) {
      throw errorMapper.fromIo(ioException);
    } catch (Exception exception) {
      throw errorMapper.fromUnexpected(exception);
    }
  }

  private String buildUrl(String path, Map<String, String> query) {
    String normalizedPath = path == null ? "" : path;
    if (!normalizedPath.startsWith("/")) {
      normalizedPath = "/" + normalizedPath;
    }
    StringBuilder builder = new StringBuilder(config.pluginBaseUrl()).append(normalizedPath);
    if (query != null && !query.isEmpty()) {
      boolean first = true;
      for (Map.Entry<String, String> entry : query.entrySet()) {
        if (entry.getValue() == null) {
          continue;
        }
        builder.append(first ? '?' : '&');
        first = false;
        builder.append(entry.getKey()).append('=').append(entry.getValue());
      }
    }
    return builder.toString();
  }

  private RequestBody toRequestBody(JsonNode body) throws IOException {
    JsonNode safeBody = body == null ? objectMapper.createObjectNode() : body;
    return RequestBody.create(objectMapper.writeValueAsBytes(safeBody), JSON);
  }

  private void addAuthHeaders(Request.Builder builder, ConnectorContext context) {
    builder.addHeader("Authorization", "Bearer " + getToken());
    Map<String, String> contextHeaders = buildContextHeaders(context);
    contextHeaders.forEach(builder::addHeader);
  }

  private Map<String, String> buildContextHeaders(ConnectorContext context) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (context == null) {
      return headers;
    }
    putIfPresent(headers, HeaderConstants.HEADER_TENANT_ID, toStringValue(context.tenantId()));
    putIfPresent(headers, HeaderConstants.HEADER_TENANT_CODE, context.tenantCode());
    putIfPresent(headers, HeaderConstants.HEADER_USER_ID, toStringValue(context.userId()));
    putIfPresent(headers, HeaderConstants.HEADER_USERNAME, context.username());
    putIfPresent(headers, HeaderConstants.HEADER_PRINCIPAL_SUB, context.principalSub());
    putIfPresent(
        headers, HeaderConstants.HEADER_ACTOR_USER_ID, toStringValue(context.actorUserId()));
    putIfPresent(
        headers, HeaderConstants.HEADER_ACTOR_TENANT_ID, toStringValue(context.actorTenantId()));
    putIfPresent(
        headers, HeaderConstants.HEADER_IMPERSONATION, String.valueOf(context.impersonation()));
    putIfPresent(headers, HeaderConstants.HEADER_TRACE_ID, context.traceId());
    putIfPresent(headers, HeaderConstants.HEADER_REQUEST_ID, context.requestId());
    return headers;
  }

  private String toStringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private void putIfPresent(Map<String, String> target, String key, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    target.put(key, value);
  }

  private String getToken() {
    Instant now = Instant.now();
    String token = cachedToken;
    Instant expiry = tokenExpiry;
    if (token != null && expiry != null && now.isBefore(expiry)) {
      return token;
    }
    synchronized (this) {
      token = cachedToken;
      expiry = tokenExpiry;
      if (token != null && expiry != null && Instant.now().isBefore(expiry)) {
        return token;
      }
      refreshToken();
      return cachedToken;
    }
  }

  private void refreshToken() {
    String body =
        "{\"username\":\"" + config.username() + "\",\"password\":\"" + config.password() + "\"}";
    Request request =
        new Request.Builder().url(config.tokenUrl()).post(RequestBody.create(body, JSON)).build();

    try (Response response = httpClient.newCall(request).execute()) {
      String payload = response.body() == null ? "" : response.body().string();
      if (!response.isSuccessful()) {
        throw errorMapper.fromHttpStatus(response.code(), payload);
      }
      JsonNode jsonNode = objectMapper.readTree(payload);
      JsonNode tokenNode = jsonNode.get("access_token");
      if (tokenNode == null || tokenNode.asText().isBlank()) {
        throw new ConnectorException(
            ErrorType.BAD_GATEWAY, "Airflow authentication token is missing");
      }
      this.cachedToken = tokenNode.asText();
      this.tokenExpiry = Instant.now().plusSeconds(23 * 3600);
    } catch (ConnectorException connectorException) {
      throw connectorException;
    } catch (IOException ioException) {
      throw errorMapper.fromIo(ioException);
    } catch (Exception exception) {
      throw errorMapper.fromUnexpected(exception);
    }
  }
}
