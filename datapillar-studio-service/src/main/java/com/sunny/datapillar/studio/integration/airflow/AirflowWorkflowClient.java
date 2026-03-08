package com.sunny.datapillar.studio.integration.airflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Workflow operations against airflow plugin. */
@Component
public class AirflowWorkflowClient {

  private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{1,63}$");

  private final AirflowHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public AirflowWorkflowClient(AirflowHttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public JsonNode deploy(JsonNode payload) {
    String dagId = resolveDagId(payload);
    long workflowId = requiredLong(payload, "workflowId");
    JsonNode workflow = requiredObject(payload, "workflow");

    var request = objectMapper.createObjectNode();
    request.put("workflow_id", workflowId);
    request.put("dag_id", dagId);
    request.set("workflow", workflow);
    return httpClient.post("/dags", request, Map.of());
  }

  public JsonNode delete(JsonNode payload) {
    return httpClient.delete("/dags/" + resolveDagId(payload), Map.of());
  }

  public JsonNode pause(JsonNode payload) {
    var body = objectMapper.createObjectNode().put("is_paused", true);
    return httpClient.patch("/dags/" + resolveDagId(payload), body, Map.of());
  }

  public JsonNode resume(JsonNode payload) {
    var body = objectMapper.createObjectNode().put("is_paused", false);
    return httpClient.patch("/dags/" + resolveDagId(payload), body, Map.of());
  }

  public JsonNode getDag(JsonNode payload) {
    return httpClient.get("/dags/" + resolveDagId(payload), Map.of());
  }

  public JsonNode listDagVersions(JsonNode payload) {
    Map<String, String> query = new LinkedHashMap<>();
    putIfPresent(query, "limit", payload.path("limit").asText(null));
    putIfPresent(query, "offset", payload.path("offset").asText(null));
    return httpClient.get("/dags/" + resolveDagId(payload) + "/versions", query);
  }

  public JsonNode getDagVersion(JsonNode payload) {
    int versionNumber = requiredInt(payload, "versionNumber");
    return httpClient.get(
        "/dags/" + resolveDagId(payload) + "/versions/" + versionNumber, Map.of());
  }

  public JsonNode triggerRun(JsonNode payload) {
    JsonNode body = payload.path("body");
    return httpClient.post(
        "/dags/" + resolveDagId(payload) + "/runs",
        body.isObject() ? body : objectMapper.createObjectNode(),
        Map.of());
  }

  public JsonNode listRuns(JsonNode payload) {
    Map<String, String> query = new LinkedHashMap<>();
    putIfPresent(query, "limit", payload.path("limit").asText(null));
    putIfPresent(query, "offset", payload.path("offset").asText(null));
    putIfPresent(query, "state", payload.path("state").asText(null));
    return httpClient.get("/dags/" + resolveDagId(payload) + "/runs", query);
  }

  public JsonNode getRun(JsonNode payload) {
    String runId = requiredString(payload, "runId");
    return httpClient.get("/dags/" + resolveDagId(payload) + "/runs/" + runId, Map.of());
  }

  public JsonNode listTasks(JsonNode payload) {
    String runId = requiredString(payload, "runId");
    return httpClient.get("/dags/" + resolveDagId(payload) + "/runs/" + runId + "/tasks", Map.of());
  }

  public JsonNode getTask(JsonNode payload) {
    String runId = requiredString(payload, "runId");
    String taskId = requiredString(payload, "taskId");
    return httpClient.get(
        "/dags/" + resolveDagId(payload) + "/runs/" + runId + "/tasks/" + taskId, Map.of());
  }

  public JsonNode getTaskLogs(JsonNode payload) {
    String runId = requiredString(payload, "runId");
    String taskId = requiredString(payload, "taskId");
    Map<String, String> query = new LinkedHashMap<>();
    putIfPresent(query, "try_number", payload.path("tryNumber").asText(null));
    return httpClient.get(
        "/dags/" + resolveDagId(payload) + "/runs/" + runId + "/tasks/" + taskId + "/logs", query);
  }

  public JsonNode rerunTask(JsonNode payload) {
    String runId = requiredString(payload, "runId");
    String taskId = requiredString(payload, "taskId");
    JsonNode body = payload.path("body");
    return httpClient.post(
        "/dags/" + resolveDagId(payload) + "/runs/" + runId + "/tasks/" + taskId + "/rerun",
        body.isObject() ? body : objectMapper.createObjectNode(),
        Map.of());
  }

  public JsonNode setTaskState(JsonNode payload) {
    String runId = requiredString(payload, "runId");
    String taskId = requiredString(payload, "taskId");
    JsonNode body = requiredObject(payload, "body");
    return httpClient.patch(
        "/dags/" + resolveDagId(payload) + "/runs/" + runId + "/tasks/" + taskId + "/state",
        body,
        Map.of());
  }

  public JsonNode clearTasks(JsonNode payload) {
    String runId = requiredString(payload, "runId");
    JsonNode body = requiredObject(payload, "body");
    return httpClient.post(
        "/dags/" + resolveDagId(payload) + "/runs/" + runId + "/clear", body, Map.of());
  }

  private String resolveDagId(JsonNode payload) {
    long workflowId = requiredLong(payload, "workflowId");
    String tenantCode = TenantContextHolder.getTenantCode();
    if (!StringUtils.hasText(tenantCode)) {
      throw new UnauthorizedException("Unauthorized access");
    }
    String normalizedTenantCode = tenantCode.trim().toLowerCase();
    if (!TENANT_CODE_PATTERN.matcher(normalizedTenantCode).matches()) {
      throw new BadRequestException("Tenant code format is invalid: %s", normalizedTenantCode);
    }
    return "dp_" + normalizedTenantCode + "_w" + workflowId;
  }

  private JsonNode requiredObject(JsonNode payload, String field) {
    JsonNode value = payload == null ? null : payload.get(field);
    if (value == null || !value.isObject()) {
      throw new BadRequestException("Missing required object field: %s", field);
    }
    return value;
  }

  private String requiredString(JsonNode payload, String field) {
    JsonNode value = payload == null ? null : payload.get(field);
    if (value == null || !StringUtils.hasText(value.asText())) {
      throw new BadRequestException("Missing required string field: %s", field);
    }
    return value.asText();
  }

  private long requiredLong(JsonNode payload, String field) {
    JsonNode value = payload == null ? null : payload.get(field);
    if (value == null || !value.canConvertToLong()) {
      throw new BadRequestException("Missing required numeric field: %s", field);
    }
    return value.longValue();
  }

  private int requiredInt(JsonNode payload, String field) {
    JsonNode value = payload == null ? null : payload.get(field);
    if (value == null || !value.canConvertToInt()) {
      throw new BadRequestException("Missing required numeric field: %s", field);
    }
    return value.intValue();
  }

  private void putIfPresent(Map<String, String> query, String key, String value) {
    if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value)) {
      return;
    }
    query.put(key, value);
  }
}
