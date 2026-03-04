package com.sunny.datapillar.connector.airflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.connector.airflow.transport.http.AirflowHttpClient;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ErrorType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Workflow domain service in airflow connector. */
public class WorkflowService {

  private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{1,63}$");

  private final AirflowHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public WorkflowService(AirflowHttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public JsonNode deploy(JsonNode payload, ConnectorContext context) {
    String dagId = resolveDagId(payload, context);
    long workflowId = requiredLong(payload, "workflowId");
    JsonNode workflow = requiredObject(payload, "workflow");

    var request = objectMapper.createObjectNode();
    request.put("workflow_id", workflowId);
    request.put("dag_id", dagId);
    request.set("workflow", workflow);
    return httpClient.post("/dags", request, Map.of(), context);
  }

  public JsonNode delete(JsonNode payload, ConnectorContext context) {
    return httpClient.delete("/dags/" + resolveDagId(payload, context), Map.of(), context);
  }

  public JsonNode pause(JsonNode payload, ConnectorContext context) {
    var body = objectMapper.createObjectNode().put("is_paused", true);
    return httpClient.patch("/dags/" + resolveDagId(payload, context), body, Map.of(), context);
  }

  public JsonNode resume(JsonNode payload, ConnectorContext context) {
    var body = objectMapper.createObjectNode().put("is_paused", false);
    return httpClient.patch("/dags/" + resolveDagId(payload, context), body, Map.of(), context);
  }

  public JsonNode getDag(JsonNode payload, ConnectorContext context) {
    return httpClient.get("/dags/" + resolveDagId(payload, context), Map.of(), context);
  }

  public JsonNode listDagVersions(JsonNode payload, ConnectorContext context) {
    Map<String, String> query = new LinkedHashMap<>();
    putIfPresent(query, "limit", payload.path("limit").asText(null));
    putIfPresent(query, "offset", payload.path("offset").asText(null));
    return httpClient.get("/dags/" + resolveDagId(payload, context) + "/versions", query, context);
  }

  public JsonNode getDagVersion(JsonNode payload, ConnectorContext context) {
    int versionNumber = requiredInt(payload, "versionNumber");
    return httpClient.get(
        "/dags/" + resolveDagId(payload, context) + "/versions/" + versionNumber,
        Map.of(),
        context);
  }

  public JsonNode triggerRun(JsonNode payload, ConnectorContext context) {
    JsonNode body = payload.path("body");
    return httpClient.post(
        "/dags/" + resolveDagId(payload, context) + "/runs",
        body.isObject() ? body : objectMapper.createObjectNode(),
        Map.of(),
        context);
  }

  public JsonNode listRuns(JsonNode payload, ConnectorContext context) {
    Map<String, String> query = new LinkedHashMap<>();
    putIfPresent(query, "limit", payload.path("limit").asText(null));
    putIfPresent(query, "offset", payload.path("offset").asText(null));
    putIfPresent(query, "state", payload.path("state").asText(null));
    return httpClient.get("/dags/" + resolveDagId(payload, context) + "/runs", query, context);
  }

  public JsonNode getRun(JsonNode payload, ConnectorContext context) {
    String runId = requiredString(payload, "runId");
    return httpClient.get(
        "/dags/" + resolveDagId(payload, context) + "/runs/" + runId, Map.of(), context);
  }

  public JsonNode listTasks(JsonNode payload, ConnectorContext context) {
    String runId = requiredString(payload, "runId");
    return httpClient.get(
        "/dags/" + resolveDagId(payload, context) + "/runs/" + runId + "/tasks", Map.of(), context);
  }

  public JsonNode getTask(JsonNode payload, ConnectorContext context) {
    String runId = requiredString(payload, "runId");
    String taskId = requiredString(payload, "taskId");
    return httpClient.get(
        "/dags/" + resolveDagId(payload, context) + "/runs/" + runId + "/tasks/" + taskId,
        Map.of(),
        context);
  }

  public JsonNode getTaskLogs(JsonNode payload, ConnectorContext context) {
    String runId = requiredString(payload, "runId");
    String taskId = requiredString(payload, "taskId");
    Map<String, String> query = new LinkedHashMap<>();
    putIfPresent(query, "try_number", payload.path("tryNumber").asText(null));
    return httpClient.get(
        "/dags/" + resolveDagId(payload, context) + "/runs/" + runId + "/tasks/" + taskId + "/logs",
        query,
        context);
  }

  public JsonNode rerunTask(JsonNode payload, ConnectorContext context) {
    String runId = requiredString(payload, "runId");
    String taskId = requiredString(payload, "taskId");
    JsonNode body = payload.path("body");
    return httpClient.post(
        "/dags/"
            + resolveDagId(payload, context)
            + "/runs/"
            + runId
            + "/tasks/"
            + taskId
            + "/rerun",
        body.isObject() ? body : objectMapper.createObjectNode(),
        Map.of(),
        context);
  }

  public JsonNode setTaskState(JsonNode payload, ConnectorContext context) {
    String runId = requiredString(payload, "runId");
    String taskId = requiredString(payload, "taskId");
    JsonNode body = requiredObject(payload, "body");
    return httpClient.patch(
        "/dags/"
            + resolveDagId(payload, context)
            + "/runs/"
            + runId
            + "/tasks/"
            + taskId
            + "/state",
        body,
        Map.of(),
        context);
  }

  public JsonNode clearTasks(JsonNode payload, ConnectorContext context) {
    String runId = requiredString(payload, "runId");
    JsonNode body = requiredObject(payload, "body");
    return httpClient.post(
        "/dags/" + resolveDagId(payload, context) + "/runs/" + runId + "/clear",
        body,
        Map.of(),
        context);
  }

  private String resolveDagId(JsonNode payload, ConnectorContext context) {
    long workflowId = requiredLong(payload, "workflowId");
    if (context == null || context.tenantCode() == null || context.tenantCode().isBlank()) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "Tenant code is required for airflow operations");
    }
    String tenantCode = context.tenantCode().trim().toLowerCase();
    if (!TENANT_CODE_PATTERN.matcher(tenantCode).matches()) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "Tenant code format is invalid: " + tenantCode);
    }
    return "dp_" + tenantCode + "_w" + workflowId;
  }

  private JsonNode requiredObject(JsonNode payload, String field) {
    JsonNode value = payload == null ? null : payload.get(field);
    if (value == null || !value.isObject()) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "Missing required object field: " + field);
    }
    return value;
  }

  private String requiredString(JsonNode payload, String field) {
    JsonNode value = payload == null ? null : payload.get(field);
    if (value == null || value.asText().isBlank()) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "Missing required string field: " + field);
    }
    return value.asText();
  }

  private long requiredLong(JsonNode payload, String field) {
    JsonNode value = payload == null ? null : payload.get(field);
    if (value == null || !value.canConvertToLong()) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "Missing required numeric field: " + field);
    }
    return value.longValue();
  }

  private int requiredInt(JsonNode payload, String field) {
    JsonNode value = payload == null ? null : payload.get(field);
    if (value == null || !value.canConvertToInt()) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "Missing required numeric field: " + field);
    }
    return value.intValue();
  }

  private void putIfPresent(Map<String, String> query, String key, String value) {
    if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
      return;
    }
    query.put(key, value);
  }
}
