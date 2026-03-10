package com.sunny.datapillar.openlineage.source.event;

import com.fasterxml.jackson.databind.JsonNode;
import io.openlineage.client.OpenLineage;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/** OpenLineage event model. */
@Getter
public class OpenLineageEvent {

  public static final String RUN_EVENT = "RUN_EVENT";
  public static final String DATASET_EVENT = "DATASET_EVENT";
  public static final String JOB_EVENT = "JOB_EVENT";

  private final OpenLineage.BaseEvent event;
  private final JsonNode rawEvent;
  private final String internalEventType;

  private OpenLineageEvent(
      OpenLineage.BaseEvent event, JsonNode rawEvent, String internalEventType) {
    this.event = event;
    this.rawEvent = rawEvent;
    this.internalEventType = internalEventType;
  }

  public static OpenLineageEvent fromRunEvent(OpenLineage.RunEvent event, JsonNode rawEvent) {
    return new OpenLineageEvent(event, rawEvent, RUN_EVENT);
  }

  public static OpenLineageEvent fromDatasetEvent(
      OpenLineage.DatasetEvent event, JsonNode rawEvent) {
    return new OpenLineageEvent(event, rawEvent, DATASET_EVENT);
  }

  public static OpenLineageEvent fromJobEvent(OpenLineage.JobEvent event, JsonNode rawEvent) {
    return new OpenLineageEvent(event, rawEvent, JOB_EVENT);
  }

  public String jobName() {
    if (event instanceof OpenLineage.RunEvent runEvent && runEvent.getJob() != null) {
      return runEvent.getJob().getName();
    }
    if (event instanceof OpenLineage.JobEvent jobEvent && jobEvent.getJob() != null) {
      return jobEvent.getJob().getName();
    }
    JsonNode jobNode = rawEvent.path("job");
    if (jobNode.isObject()) {
      return trimToNull(jobNode.path("name").asText(null));
    }
    return null;
  }

  public String jobNamespace() {
    if (event instanceof OpenLineage.RunEvent runEvent && runEvent.getJob() != null) {
      return runEvent.getJob().getNamespace();
    }
    if (event instanceof OpenLineage.JobEvent jobEvent && jobEvent.getJob() != null) {
      return jobEvent.getJob().getNamespace();
    }
    JsonNode jobNode = rawEvent.path("job");
    if (jobNode.isObject()) {
      return trimToNull(jobNode.path("namespace").asText(null));
    }
    return null;
  }

  public String runId() {
    if (event instanceof OpenLineage.RunEvent runEvent
        && runEvent.getRun() != null
        && runEvent.getRun().getRunId() != null) {
      return runEvent.getRun().getRunId().toString();
    }
    JsonNode runNode = rawEvent.path("run");
    if (runNode.isObject()) {
      return trimToNull(runNode.path("runId").asText(null));
    }
    return null;
  }

  public String producer() {
    URI producer = event.getProducer();
    if (producer != null) {
      return producer.toString();
    }
    return trimToNull(rawEvent.path("producer").asText(null));
  }

  public ZonedDateTime eventTime() {
    ZonedDateTime eventTime = event.getEventTime();
    if (eventTime != null) {
      return eventTime;
    }
    String text = trimToNull(rawEvent.path("eventTime").asText(null));
    if (text == null) {
      return ZonedDateTime.now(ZoneOffset.UTC);
    }
    return ZonedDateTime.parse(text);
  }

  public Instant eventInstant() {
    return eventTime().toInstant();
  }

  public String runEventType() {
    if (event instanceof OpenLineage.RunEvent runEvent && runEvent.getEventType() != null) {
      return runEvent.getEventType().name();
    }
    return trimToNull(rawEvent.path("eventType").asText(null));
  }

  public String sqlQuery() {
    JsonNode sqlNode = rawEvent.path("job").path("facets").path("sql");
    if (!sqlNode.isObject()) {
      return null;
    }
    return trimToNull(sqlNode.path("query").asText(null));
  }

  public boolean looksLikeGravitinoSource() {
    String producer = producer();
    if (producer != null && producer.toLowerCase().contains("gravitino")) {
      return true;
    }
    String jobName = jobName();
    if (jobName != null && jobName.startsWith("gravitino.")) {
      return true;
    }
    for (JsonNode datasetNode : datasetNodes()) {
      if (datasetNode.path("facets").path("gravitino").isObject()) {
        return true;
      }
    }
    return false;
  }

  public Optional<Long> facetTenantId() {
    for (JsonNode facetNode : gravitinoFacetNodes()) {
      Optional<Long> parsed = parsePositiveLong(facetNode.path("tenantId"));
      if (parsed.isPresent()) {
        return parsed;
      }
    }

    for (JsonNode datasetNode : datasetNodes()) {
      Optional<Long> parsed =
          parseTenantIdFromNamespace(trimToNull(datasetNode.path("namespace").asText(null)));
      if (parsed.isPresent()) {
        return parsed;
      }
    }

    Optional<Long> parsedFromJobNamespace = parseTenantIdFromNamespace(jobNamespace());
    if (parsedFromJobNamespace.isPresent()) {
      return parsedFromJobNamespace;
    }
    return Optional.empty();
  }

  public Optional<String> facetTenantCode() {
    for (JsonNode facetNode : gravitinoFacetNodes()) {
      String value = trimToNull(facetNode.path("tenantCode").asText(null));
      if (value != null) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

  public Optional<String> facetTenantName() {
    for (JsonNode facetNode : gravitinoFacetNodes()) {
      String value = trimToNull(facetNode.path("tenantName").asText(null));
      if (value != null) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

  public List<JsonNode> datasetNodes() {
    List<JsonNode> datasets = new ArrayList<>();

    JsonNode inputs = rawEvent.path("inputs");
    if (inputs.isArray()) {
      inputs.forEach(datasets::add);
    }

    JsonNode outputs = rawEvent.path("outputs");
    if (outputs.isArray()) {
      outputs.forEach(datasets::add);
    }

    JsonNode dataset = rawEvent.path("dataset");
    if (dataset.isObject()) {
      datasets.add(dataset);
    }

    return datasets;
  }

  public List<JsonNode> inputDatasetNodes() {
    List<JsonNode> datasets = new ArrayList<>();
    JsonNode inputs = rawEvent.path("inputs");
    if (inputs.isArray()) {
      inputs.forEach(datasets::add);
    }
    return datasets;
  }

  public List<JsonNode> outputDatasetNodes() {
    List<JsonNode> datasets = new ArrayList<>();
    JsonNode outputs = rawEvent.path("outputs");
    if (outputs.isArray()) {
      outputs.forEach(datasets::add);
    }
    JsonNode dataset = rawEvent.path("dataset");
    if (dataset.isObject()) {
      datasets.add(dataset);
    }
    return datasets;
  }

  public String serializeForStorage() {
    return rawEvent.toString();
  }

  public String payloadFingerprint() {
    return Integer.toHexString(rawEvent.toString().hashCode());
  }

  public Iterator<String> rootFieldNames() {
    return rawEvent.fieldNames();
  }

  private List<JsonNode> gravitinoFacetNodes() {
    List<JsonNode> nodes = new ArrayList<>();

    for (JsonNode datasetNode : datasetNodes()) {
      JsonNode facetNode = datasetNode.path("facets").path("gravitino");
      if (facetNode.isObject()) {
        nodes.add(facetNode);
      }
    }

    JsonNode jobFacet = rawEvent.path("job").path("facets").path("gravitino");
    if (jobFacet.isObject()) {
      nodes.add(jobFacet);
    }

    JsonNode runFacet = rawEvent.path("run").path("facets").path("gravitino");
    if (runFacet.isObject()) {
      nodes.add(runFacet);
    }

    JsonNode eventFacet = rawEvent.path("facets").path("gravitino");
    if (eventFacet.isObject()) {
      nodes.add(eventFacet);
    }

    return nodes;
  }

  private Optional<Long> parsePositiveLong(JsonNode valueNode) {
    if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
      return Optional.empty();
    }

    if (valueNode.isNumber()) {
      long value = valueNode.asLong();
      return value > 0 ? Optional.of(value) : Optional.empty();
    }

    if (valueNode.isTextual()) {
      String raw = trimToNull(valueNode.asText(null));
      if (raw == null) {
        return Optional.empty();
      }
      try {
        long value = Long.parseLong(raw);
        return value > 0 ? Optional.of(value) : Optional.empty();
      } catch (NumberFormatException ignored) {
        return Optional.empty();
      }
    }

    return Optional.empty();
  }

  private Optional<Long> parseTenantIdFromNamespace(String namespace) {
    if (namespace == null) {
      return Optional.empty();
    }

    String lower = namespace.toLowerCase();
    String token = "gravitino://tenant/";
    int start = lower.indexOf(token);
    if (start < 0) {
      return Optional.empty();
    }

    int idStart = start + token.length();
    int idEnd = idStart;
    while (idEnd < namespace.length() && Character.isDigit(namespace.charAt(idEnd))) {
      idEnd++;
    }
    if (idEnd <= idStart) {
      return Optional.empty();
    }
    try {
      long value = Long.parseLong(namespace.substring(idStart, idEnd));
      return value > 0 ? Optional.of(value) : Optional.empty();
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
