package com.sunny.datapillar.openlineage.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.openlineage.config.OpenLineageRuntimeConfig;
import com.sunny.datapillar.openlineage.pipeline.EventTopicPublisher;
import com.sunny.datapillar.openlineage.source.event.DeadLetterEvent;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.dto.request.DlqReplayRequest;
import com.sunny.datapillar.openlineage.web.dto.request.EventIngestRequest;
import com.sunny.datapillar.openlineage.web.dto.response.DlqReplayResponse;
import com.sunny.datapillar.openlineage.web.dto.response.EventIngestAckResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Event service for events ingress and event DLQ replay. */
@Service
public class EventService {

  private static final Set<String> REBUILD_ALLOWED_ROLES = Set.of("ADMIN", "OWNER");

  private final ObjectMapper openLineageObjectMapper;
  private final EventTopicPublisher eventTopicPublisher;
  private final OpenLineageRuntimeConfig runtimeProperties;

  public EventService(
      @Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper,
      EventTopicPublisher eventTopicPublisher,
      OpenLineageRuntimeConfig runtimeProperties) {
    this.openLineageObjectMapper = openLineageObjectMapper;
    this.eventTopicPublisher = eventTopicPublisher;
    this.runtimeProperties = runtimeProperties;
  }

  public EventIngestAckResponse ingest(EventIngestRequest request) {
    if (request == null) {
      throw new BadRequestException("events payload must be JSON object");
    }
    JsonNode payload = request.toPayloadNode(openLineageObjectMapper);
    if (payload == null || !payload.isObject()) {
      throw new BadRequestException("events payload must be JSON object");
    }

    TrustedIdentityContext identity = requireIdentity();
    Long tenantId = requireTenantId();
    String tenantCode = requireTenantCode();

    String messageId = UUID.randomUUID().toString();
    long enqueuedAt = System.currentTimeMillis();
    String messageKey = tenantId + "|" + messageId;

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(EventHeaders.MESSAGE_ID, messageId);
    headers.put(EventHeaders.TENANT_ID, String.valueOf(tenantId));
    headers.put(EventHeaders.TENANT_CODE, tenantCode);
    headers.put(EventHeaders.SOURCE, resolveSource(payload));
    headers.put(EventHeaders.ATTEMPT, "0");
    headers.put(EventHeaders.ENQUEUED_AT, String.valueOf(enqueuedAt));
    headers.put(EventHeaders.SCHEMA_VERSION, EventHeaders.EVENTS_SCHEMA);

    eventTopicPublisher.send(payload.toString(), messageKey, headers);

    return EventIngestAckResponse.builder()
        .status("accepted")
        .eventType(trimToNull(payload.path("eventType").asText(null)))
        .runId(trimToNull(payload.path("run").path("runId").asText(null)))
        .tenantId(tenantId)
        .messageId(messageId)
        .build();
  }

  public DlqReplayResponse replayEventsDlq(DlqReplayRequest request) {
    if (request == null) {
      throw new BadRequestException("replay payload is missing");
    }
    if (!hasText(request.payload())) {
      throw new BadRequestException("payload is empty");
    }

    TrustedIdentityContext identity = requireIdentity();
    Long tenantId = requireTenantId();
    String tenantCode = requireTenantCode();
    requireRebuildPermission(identity);
    int resetAttempt = request.attempt() == null ? 0 : Math.max(0, request.attempt());

    try {
      DeadLetterEvent deadLetterEvent =
          openLineageObjectMapper.readValue(request.payload(), DeadLetterEvent.class);
      Map<String, String> headers =
          deadLetterEvent.getOriginalHeaders() == null
              ? new LinkedHashMap<>()
              : new LinkedHashMap<>(deadLetterEvent.getOriginalHeaders());
      validateReplayTenant(tenantId, headers.get(EventHeaders.TENANT_ID));
      validateReplayTenantCode(tenantCode, headers.get(EventHeaders.TENANT_CODE));

      if (!hasText(deadLetterEvent.getOriginalBody())) {
        throw new BadRequestException("DLQ replay body is empty");
      }
      if (!hasText(headers.get(EventHeaders.MESSAGE_ID))) {
        headers.put(EventHeaders.MESSAGE_ID, UUID.randomUUID().toString());
      }
      headers.put(EventHeaders.ATTEMPT, String.valueOf(resetAttempt));
      headers.put(EventHeaders.ENQUEUED_AT, String.valueOf(System.currentTimeMillis()));
      headers.put(EventHeaders.SCHEMA_VERSION, EventHeaders.EVENTS_SCHEMA);

      String replayKey = tenantId + "|" + headers.get(EventHeaders.MESSAGE_ID);
      eventTopicPublisher.send(deadLetterEvent.getOriginalBody(), replayKey, headers);
      return new DlqReplayResponse(
          "accepted",
          tenantId,
          runtimeProperties.getMq().getTopic().getEventsDlq(),
          runtimeProperties.getMq().getTopic().getEvents(),
          headers.get(EventHeaders.MESSAGE_ID));
    } catch (JsonProcessingException ex) {
      throw new BadRequestException(ex, "DLQ replay payload is invalid");
    }
  }

  private String resolveSource(JsonNode payload) {
    String producer = trimToNull(payload.path("producer").asText(null));
    if (!hasText(producer)) {
      return "unknown";
    }
    String lower = producer.toLowerCase();
    if (lower.contains("gravitino")) {
      return "gravitino";
    }
    if (lower.contains("hive")) {
      return "hive";
    }
    if (lower.contains("flink")) {
      return "flink";
    }
    if (lower.contains("spark")) {
      return "spark";
    }
    return "unknown";
  }

  private void validateReplayTenant(Long expectedTenantId, String payloadTenantId) {
    if (!hasText(payloadTenantId)) {
      throw new ForbiddenException("tenantId access denied");
    }
    try {
      long value = Long.parseLong(payloadTenantId);
      if (!Long.valueOf(value).equals(expectedTenantId)) {
        throw new ForbiddenException("tenantId access denied");
      }
    } catch (NumberFormatException ex) {
      throw new ForbiddenException("tenantId access denied");
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private Long requireTenantId() {
    Long tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null || tenantId <= 0) {
      throw new UnauthorizedException("trusted_identity_tenant_id_missing");
    }
    return tenantId;
  }

  private String requireTenantCode() {
    String tenantCode = TenantContextHolder.getTenantCode();
    if (!hasText(tenantCode)) {
      throw new UnauthorizedException("trusted_identity_tenant_code_missing");
    }
    return tenantCode.trim();
  }

  private TrustedIdentityContext requireIdentity() {
    TrustedIdentityContext identity = TrustedIdentityContextHolder.get();
    if (identity == null || identity.principalType() == null || identity.tenantId() == null) {
      throw new UnauthorizedException("trusted_identity_context_missing");
    }
    return identity;
  }

  private void requireRebuildPermission(TrustedIdentityContext identity) {
    if (identity.roles().stream().noneMatch(REBUILD_ALLOWED_ROLES::contains)) {
      throw new ForbiddenException("No rebuild permission");
    }
  }

  private void validateReplayTenantCode(String expectedTenantCode, String payloadTenantCode) {
    if (!hasText(payloadTenantCode) || !expectedTenantCode.equals(payloadTenantCode.trim())) {
      throw new ForbiddenException("tenantCode access denied");
    }
  }

  private String trimToNull(String value) {
    if (!hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
