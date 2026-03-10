package com.sunny.datapillar.studio.integration.airflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ConflictException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.studio.config.AirflowConfig;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.util.UserContextUtil;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** HTTP client for datapillar-airflow-plugin REST API. */
@Component
public class AirflowHttpClient {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final AirflowConfig config;
  private final ObjectMapper objectMapper;
  private final OkHttpClient httpClient;

  private volatile String cachedToken;
  private volatile Instant tokenExpiry;

  public AirflowHttpClient(AirflowConfig config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(
                resolvePositive(config.getConnectTimeoutMs(), 5000), TimeUnit.MILLISECONDS)
            .readTimeout(resolvePositive(config.getReadTimeoutMs(), 30000), TimeUnit.MILLISECONDS)
            .writeTimeout(resolvePositive(config.getReadTimeoutMs(), 30000), TimeUnit.MILLISECONDS)
            .build();
  }

  public JsonNode get(String path, Map<String, String> query) {
    return execute("GET", path, null, query);
  }

  public JsonNode post(String path, JsonNode body, Map<String, String> query) {
    return execute("POST", path, body, query);
  }

  public JsonNode patch(String path, JsonNode body, Map<String, String> query) {
    return execute("PATCH", path, body, query);
  }

  public JsonNode delete(String path, Map<String, String> query) {
    return execute("DELETE", path, null, query);
  }

  private JsonNode execute(String method, String path, JsonNode body, Map<String, String> query) {
    try {
      Request.Builder builder = new Request.Builder().url(buildUrl(path, query));
      addAuthHeaders(builder);

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
          throw mapHttpStatus(response.code(), payload);
        }
        if (!StringUtils.hasText(payload)) {
          return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(payload);
      }
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (IOException ioException) {
      throw new ServiceUnavailableException(
          ioException, "Airflow connectivity failure: %s", ioException.getMessage());
    }
  }

  private String buildUrl(String path, Map<String, String> query) {
    String normalizedPath = StringUtils.hasText(path) ? path.trim() : "";
    if (!normalizedPath.startsWith("/")) {
      normalizedPath = "/" + normalizedPath;
    }
    StringBuilder builder = new StringBuilder(pluginBaseUrl()).append(normalizedPath);
    if (query != null && !query.isEmpty()) {
      boolean first = true;
      for (Map.Entry<String, String> entry : query.entrySet()) {
        if (!StringUtils.hasText(entry.getValue())) {
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

  private void addAuthHeaders(Request.Builder builder) {
    builder.addHeader("Authorization", "Bearer " + getToken());
    buildContextHeaders().forEach(builder::addHeader);
  }

  private Map<String, String> buildContextHeaders() {
    Map<String, String> headers = new LinkedHashMap<>();
    Long tenantId = TenantContextHolder.getTenantId();
    String tenantCode = TenantContextHolder.getTenantCode();
    Long userId = UserContextUtil.getUserId();
    String username = UserContextUtil.getUsername();

    putIfPresent(headers, HeaderConstants.HEADER_TENANT_ID, toStringValue(tenantId));
    putIfPresent(headers, HeaderConstants.HEADER_TENANT_CODE, tenantCode);
    putIfPresent(headers, HeaderConstants.HEADER_USER_ID, toStringValue(userId));
    putIfPresent(headers, HeaderConstants.HEADER_USERNAME, username);
    putIfPresent(
        headers,
        HeaderConstants.HEADER_ACTOR_USER_ID,
        toStringValue(TenantContextHolder.getActorUserId()));
    putIfPresent(
        headers,
        HeaderConstants.HEADER_ACTOR_TENANT_ID,
        toStringValue(TenantContextHolder.getActorTenantId()));
    headers.put(
        HeaderConstants.HEADER_IMPERSONATION,
        String.valueOf(TenantContextHolder.isImpersonation()));
    putIfPresent(headers, HeaderConstants.HEADER_TRACE_ID, UserContextUtil.getTraceId());
    return headers;
  }

  private String toStringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private void putIfPresent(Map<String, String> headers, String key, String value) {
    if (!StringUtils.hasText(value)) {
      return;
    }
    headers.put(key, value.trim());
  }

  private String getToken() {
    Instant now = Instant.now();
    if (cachedToken != null && tokenExpiry != null && now.isBefore(tokenExpiry)) {
      return cachedToken;
    }
    synchronized (this) {
      now = Instant.now();
      if (cachedToken != null && tokenExpiry != null && now.isBefore(tokenExpiry)) {
        return cachedToken;
      }
      refreshToken();
      return cachedToken;
    }
  }

  private void refreshToken() {
    String body =
        "{\"username\":\""
            + requiredText(config.getUsername(), "airflow.username")
            + "\",\"password\":\""
            + requiredText(config.getPassword(), "airflow.password")
            + "\"}";
    Request request =
        new Request.Builder().url(tokenUrl()).post(RequestBody.create(body, JSON)).build();

    try (Response response = httpClient.newCall(request).execute()) {
      String payload = response.body() == null ? "" : response.body().string();
      if (!response.isSuccessful()) {
        throw mapHttpStatus(response.code(), payload);
      }
      JsonNode jsonNode = objectMapper.readTree(payload);
      JsonNode tokenNode = jsonNode.get("access_token");
      if (tokenNode == null || !StringUtils.hasText(tokenNode.asText())) {
        throw new ServiceUnavailableException("Airflow authentication token is missing");
      }
      this.cachedToken = tokenNode.asText();
      this.tokenExpiry = Instant.now().plusSeconds(23 * 3600);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (IOException ioException) {
      throw new ServiceUnavailableException(
          ioException, "Airflow authentication failed: %s", ioException.getMessage());
    }
  }

  private RuntimeException mapHttpStatus(int statusCode, String responseBody) {
    String message =
        "Airflow request failed: status=" + statusCode + ", body=" + safeBody(responseBody);
    if (statusCode == 400) {
      return new BadRequestException(message);
    }
    if (statusCode == 401) {
      return new UnauthorizedException(message);
    }
    if (statusCode == 403) {
      return new ForbiddenException(message);
    }
    if (statusCode == 404) {
      return new NotFoundException(message);
    }
    if (statusCode == 409) {
      return new ConflictException(message);
    }
    if (statusCode >= 500) {
      return new ServiceUnavailableException(message);
    }
    return new ServiceUnavailableException(message);
  }

  private String safeBody(String responseBody) {
    if (responseBody == null) {
      return "";
    }
    String normalized = responseBody.trim();
    if (normalized.length() <= 512) {
      return normalized;
    }
    return normalized.substring(0, 512);
  }

  private String requiredText(String value, String propertyName) {
    if (!StringUtils.hasText(value)) {
      throw new InternalException("Missing required config: %s", propertyName);
    }
    return value.trim();
  }

  private int resolvePositive(Integer value, int defaultValue) {
    if (value == null || value <= 0) {
      return defaultValue;
    }
    return value;
  }

  private String tokenUrl() {
    return normalizedEndpoint() + "/auth/token";
  }

  private String pluginBaseUrl() {
    String pluginPath = requiredText(config.getPluginPath(), "airflow.plugin-path");
    if (!pluginPath.startsWith("/")) {
      pluginPath = "/" + pluginPath;
    }
    return normalizedEndpoint() + pluginPath;
  }

  private String normalizedEndpoint() {
    String endpoint = requiredText(config.getEndpoint(), "airflow.endpoint");
    if (endpoint.endsWith("/")) {
      return endpoint.substring(0, endpoint.length() - 1);
    }
    return endpoint;
  }
}
