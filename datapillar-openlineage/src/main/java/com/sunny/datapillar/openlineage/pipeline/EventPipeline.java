package com.sunny.datapillar.openlineage.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.sink.GraphSink;
import com.sunny.datapillar.openlineage.source.FlinkSource;
import com.sunny.datapillar.openlineage.source.GravitinoOpenlineageSource;
import com.sunny.datapillar.openlineage.source.HiveSource;
import com.sunny.datapillar.openlineage.source.OpenLineageSource;
import com.sunny.datapillar.openlineage.source.OpenLineageSourceModels;
import com.sunny.datapillar.openlineage.source.SparkSource;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Event pipeline for graph consume flow. */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = "${openlineage.mq.topic.events:dp.openlineage.events}",
    consumerGroup = "${openlineage.mq.group.graph-consumer:ol-graph-consumer}",
    consumeMode = ConsumeMode.ORDERLY,
    messageModel = MessageModel.CLUSTERING)
public class EventPipeline implements RocketMQListener<MessageExt> {

  private static final String DW_SCOPE = "DW";
  private static final Long DW_OWNER_USER_ID = 0L;

  private final ObjectMapper openLineageObjectMapper;
  private final GraphSink graphSink;
  private final EmbeddingBindingMapper embeddingBindingMapper;
  private final EmbeddingEventPublisher embeddingEventPublisher;
  private final EventTopicPublisher eventTopicPublisher;
  private final DeadLetterPublisher deadLetterPublisher;
  private final MqRetryPolicy retryPolicy;
  private final EventTenantResolver eventTenantResolver;
  private final GravitinoOpenlineageSource gravitinoOpenlineageSource;
  private final HiveSource hiveSource;
  private final FlinkSource flinkSource;
  private final SparkSource sparkSource;

  public EventPipeline(
      @Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper,
      GraphSink graphSink,
      EmbeddingBindingMapper embeddingBindingMapper,
      EmbeddingEventPublisher embeddingEventPublisher,
      EventTopicPublisher eventTopicPublisher,
      DeadLetterPublisher deadLetterPublisher,
      MqRetryPolicy retryPolicy,
      EventTenantResolver eventTenantResolver,
      GravitinoOpenlineageSource gravitinoOpenlineageSource,
      HiveSource hiveSource,
      FlinkSource flinkSource,
      SparkSource sparkSource) {
    this.openLineageObjectMapper = openLineageObjectMapper;
    this.graphSink = graphSink;
    this.embeddingBindingMapper = embeddingBindingMapper;
    this.embeddingEventPublisher = embeddingEventPublisher;
    this.eventTopicPublisher = eventTopicPublisher;
    this.deadLetterPublisher = deadLetterPublisher;
    this.retryPolicy = retryPolicy;
    this.eventTenantResolver = eventTenantResolver;
    this.gravitinoOpenlineageSource = gravitinoOpenlineageSource;
    this.hiveSource = hiveSource;
    this.flinkSource = flinkSource;
    this.sparkSource = sparkSource;
  }

  @Override
  public void onMessage(MessageExt mqMessage) {
    int currentAttempt = headerInt(mqMessage, EventHeaders.ATTEMPT, 0);
    String body = messageBody(mqMessage);
    Map<String, String> headers = extractHeaders(mqMessage);
    String messageKey =
        hasText(mqMessage.getKeys()) ? mqMessage.getKeys() : fallbackMessageKey(headers);

    try {
      consume(mqMessage);
    } catch (BadRequestException | NotFoundException | ForbiddenException ex) {
      log.warn(
          "graph_consumer_non_retryable tenantId={} messageId={} attempt={} reason={}",
          headers.get(EventHeaders.TENANT_ID),
          headers.get(EventHeaders.MESSAGE_ID),
          currentAttempt,
          ex.getMessage());
      deadLetterPublisher.publishEvent(ex.getMessage(), currentAttempt, body, headers);
    } catch (Throwable ex) {
      int nextAttempt = currentAttempt + 1;
      if (retryPolicy.shouldRetry(nextAttempt)) {
        int delaySeconds = retryPolicy.nextDelaySeconds(currentAttempt);
        headers.put(EventHeaders.ATTEMPT, String.valueOf(nextAttempt));
        headers.put(EventHeaders.ENQUEUED_AT, String.valueOf(System.currentTimeMillis()));
        eventTopicPublisher.retry(body, messageKey, headers, delaySeconds);
        log.warn(
            "graph_consumer_retry tenantId={} messageId={} attempt={} delaySeconds={} reason={}",
            headers.get(EventHeaders.TENANT_ID),
            headers.get(EventHeaders.MESSAGE_ID),
            nextAttempt,
            delaySeconds,
            ex.getMessage());
      } else {
        deadLetterPublisher.publishEvent(ex.getMessage(), currentAttempt, body, headers);
        log.error(
            "graph_consumer_dlq tenantId={} messageId={} attempt={} reason={}",
            headers.get(EventHeaders.TENANT_ID),
            headers.get(EventHeaders.MESSAGE_ID),
            currentAttempt,
            ex.getMessage(),
            ex);
      }
    }
  }

  public void consume(MessageExt mqMessage) {
    String body = messageBody(mqMessage);
    if (!hasText(body)) {
      throw new BadRequestException("event message body is empty");
    }

    Long headerTenantId = headerLong(mqMessage, EventHeaders.TENANT_ID);
    String headerTenantCode = headerText(mqMessage, EventHeaders.TENANT_CODE);
    JsonNode payload = parsePayload(body);
    OpenLineageSource source = requireSource(payload);
    OpenLineageSourceModels models = source.readModels(headerTenantId, payload);
    Tenant tenant = eventTenantResolver.resolve(headerTenantId, headerTenantCode, models);

    EmbeddingBindingMapper.RuntimeModelRow runtime = requireDwRuntime(tenant.getTenantId());
    String sourceEventMessageId = trimToNull(headerText(mqMessage, EventHeaders.MESSAGE_ID));
    List<EmbeddingTaskPayload> tasks =
        graphSink.apply(
            tenant,
            models,
            runtime,
            runtime.getRevision(),
            EmbeddingTriggerType.REALTIME,
            sourceEventMessageId);
    embeddingEventPublisher.publishTasks(tasks);
  }

  private JsonNode parsePayload(String body) {
    try {
      JsonNode payload = openLineageObjectMapper.readTree(body);
      if (!payload.isObject()) {
        throw new BadRequestException("events payload must be JSON object");
      }
      return payload;
    } catch (JsonProcessingException ex) {
      throw new BadRequestException(ex, "event payload parse failed");
    }
  }

  private OpenLineageSource requireSource(JsonNode payload) {
    List<OpenLineageSource> sources =
        List.of(gravitinoOpenlineageSource, hiveSource, flinkSource, sparkSource);
    for (OpenLineageSource source : sources) {
      if (source.supports(payload)) {
        return source;
      }
    }
    throw new BadRequestException("Unsupported OpenLineage source");
  }

  private EmbeddingBindingMapper.RuntimeModelRow requireDwRuntime(Long tenantId) {
    List<EmbeddingBindingMapper.RuntimeModelRow> rows =
        embeddingBindingMapper.selectDwRuntimeByTenant(tenantId, DW_SCOPE, DW_OWNER_USER_ID);
    if (rows == null || rows.isEmpty()) {
      throw new BadRequestException("DW embedding model is not configured");
    }
    if (rows.size() > 1) {
      throw new BadRequestException(
          "DW embedding binding duplicated: tenantId=%s scope=%s ownerUserId=%s",
          tenantId, DW_SCOPE, DW_OWNER_USER_ID);
    }
    EmbeddingBindingMapper.RuntimeModelRow row = rows.getFirst();
    validateRuntime(row);
    return row;
  }

  private void validateRuntime(EmbeddingBindingMapper.RuntimeModelRow row) {
    if (row.getRevision() == null || row.getRevision() <= 0) {
      throw new BadRequestException("DW embedding binding revision is invalid");
    }
    if (!"embeddings".equalsIgnoreCase(row.getModelType())) {
      throw new BadRequestException("Model type must be embeddings");
    }
    if (!"ACTIVE".equalsIgnoreCase(row.getStatus())) {
      throw new BadRequestException("Model is not active");
    }
    if (!hasText(row.getProviderCode())) {
      throw new BadRequestException("Model providerCode is empty");
    }
    if (!hasText(row.getProviderModelId())) {
      throw new BadRequestException("Model providerModelId is empty");
    }
    if (!hasText(row.getApiKey())) {
      throw new BadRequestException("Model apiKey is empty");
    }
  }

  private String messageBody(MessageExt messageExt) {
    if (messageExt == null || messageExt.getBody() == null) {
      return null;
    }
    return new String(messageExt.getBody(), StandardCharsets.UTF_8);
  }

  private Map<String, String> extractHeaders(MessageExt mqMessage) {
    Map<String, String> headers = new LinkedHashMap<>();
    putIfText(headers, EventHeaders.MESSAGE_ID, headerText(mqMessage, EventHeaders.MESSAGE_ID));
    putIfText(headers, EventHeaders.TENANT_ID, headerText(mqMessage, EventHeaders.TENANT_ID));
    putIfText(headers, EventHeaders.TENANT_CODE, headerText(mqMessage, EventHeaders.TENANT_CODE));
    putIfText(headers, EventHeaders.SOURCE, headerText(mqMessage, EventHeaders.SOURCE));
    putIfText(headers, EventHeaders.ATTEMPT, headerText(mqMessage, EventHeaders.ATTEMPT));
    putIfText(headers, EventHeaders.ENQUEUED_AT, headerText(mqMessage, EventHeaders.ENQUEUED_AT));
    putIfText(
        headers, EventHeaders.SCHEMA_VERSION, headerText(mqMessage, EventHeaders.SCHEMA_VERSION));
    return headers;
  }

  private void putIfText(Map<String, String> headers, String key, String value) {
    if (hasText(value)) {
      headers.put(key, value);
    }
  }

  private String fallbackMessageKey(Map<String, String> headers) {
    String tenantId = trimToNull(headers.get(EventHeaders.TENANT_ID));
    String messageId = trimToNull(headers.get(EventHeaders.MESSAGE_ID));
    if (tenantId != null && messageId != null) {
      return tenantId + "|" + messageId;
    }
    return UUID.randomUUID().toString();
  }

  private String headerText(MessageExt messageExt, String key) {
    if (messageExt == null || key == null) {
      return null;
    }
    String value = trimToNull(messageExt.getUserProperty(key));
    if (value != null) {
      return value;
    }
    return trimToNull(messageExt.getProperty(key));
  }

  private Long headerLong(MessageExt messageExt, String key) {
    String value = headerText(messageExt, key);
    if (!hasText(value)) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private int headerInt(MessageExt messageExt, String key, int fallback) {
    String value = headerText(messageExt, key);
    if (!hasText(value)) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String trimToNull(String value) {
    if (!hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
