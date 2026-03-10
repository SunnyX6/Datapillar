package com.sunny.datapillar.openlineage.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.sink.GraphSink;
import com.sunny.datapillar.openlineage.source.GravitinoDBSource;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.source.event.RebuildCommand;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

/** Pipeline for rebuild command consume flow. */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = "${openlineage.mq.topic.rebuild-command:dp.openlineage.rebuild.command}",
    consumerGroup = "${openlineage.mq.group.rebuild-consumer:ol-rebuild-consumer}",
    consumeMode = ConsumeMode.ORDERLY,
    messageModel = MessageModel.CLUSTERING)
public class RebuildCommandPipeline implements RocketMQListener<MessageExt> {

  private final GraphSink graphSink;
  private final GravitinoDBSource gravitinoDBSource;
  private final EmbeddingEventPublisher embeddingEventPublisher;
  private final RebuildCommandTopicPublisher rebuildCommandTopicPublisher;
  private final DeadLetterPublisher deadLetterPublisher;
  private final MqRetryPolicy retryPolicy;
  private final ObjectMapper openLineageObjectMapper;

  public RebuildCommandPipeline(
      GraphSink graphSink,
      GravitinoDBSource gravitinoDBSource,
      EmbeddingEventPublisher embeddingEventPublisher,
      RebuildCommandTopicPublisher rebuildCommandTopicPublisher,
      DeadLetterPublisher deadLetterPublisher,
      MqRetryPolicy retryPolicy,
      @Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper) {
    this.graphSink = graphSink;
    this.gravitinoDBSource = gravitinoDBSource;
    this.embeddingEventPublisher = embeddingEventPublisher;
    this.rebuildCommandTopicPublisher = rebuildCommandTopicPublisher;
    this.deadLetterPublisher = deadLetterPublisher;
    this.retryPolicy = retryPolicy;
    this.openLineageObjectMapper = openLineageObjectMapper;
  }

  @Override
  public void onMessage(MessageExt mqMessage) {
    int currentAttempt = headerInt(mqMessage, EventHeaders.ATTEMPT, 0);
    String body = messageBody(mqMessage);
    Map<String, String> headers = extractHeaders(mqMessage);
    String messageKey = fallbackMessageKey(headers, body);

    try {
      consume(mqMessage);
    } catch (BadRequestException | NotFoundException | ForbiddenException ex) {
      log.warn(
          "rebuild_consumer_non_retryable tenantId={} rebuildId={} attempt={} reason={}",
          headers.get(EventHeaders.TENANT_ID),
          headers.get(EventHeaders.REBUILD_ID),
          currentAttempt,
          ex.getMessage());
      deadLetterPublisher.publishRebuild(ex.getMessage(), currentAttempt, body, headers);
    } catch (Throwable ex) {
      int nextAttempt = currentAttempt + 1;
      if (retryPolicy.shouldRetry(nextAttempt)) {
        int delaySeconds = retryPolicy.nextDelaySeconds(currentAttempt);
        headers.put(EventHeaders.ATTEMPT, String.valueOf(nextAttempt));
        headers.put(EventHeaders.ENQUEUED_AT, String.valueOf(System.currentTimeMillis()));
        rebuildCommandTopicPublisher.retry(body, messageKey, headers, delaySeconds);
        log.warn(
            "rebuild_consumer_retry tenantId={} rebuildId={} attempt={} delaySeconds={} reason={}",
            headers.get(EventHeaders.TENANT_ID),
            headers.get(EventHeaders.REBUILD_ID),
            nextAttempt,
            delaySeconds,
            ex.getMessage());
      } else {
        deadLetterPublisher.publishRebuild(ex.getMessage(), currentAttempt, body, headers);
        log.error(
            "rebuild_consumer_dlq tenantId={} rebuildId={} attempt={} reason={}",
            headers.get(EventHeaders.TENANT_ID),
            headers.get(EventHeaders.REBUILD_ID),
            currentAttempt,
            ex.getMessage(),
            ex);
      }
    }
  }

  public void consume(MessageExt mqMessage) {
    RebuildCommand command = parseCommand(messageBody(mqMessage));
    Long tenantId = command.getTenantId();
    if (tenantId == null || tenantId <= 0) {
      throw new BadRequestException("rebuild command tenantId is invalid");
    }
    String tenantCode = trimToNull(command.getTenantCode());
    if (!hasText(tenantCode)) {
      throw new BadRequestException("rebuild command tenantCode is invalid");
    }
    Long bindingRevision = command.getBindingRevision();
    if (bindingRevision == null || bindingRevision <= 0) {
      throw new BadRequestException("rebuild command bindingRevision is invalid");
    }
    EmbeddingBindingMapper.RuntimeModelRow runtime = buildRuntime(command);

    Tenant tenant = new Tenant();
    tenant.setTenantId(tenantId);
    tenant.setTenantCode(tenantCode);

    String sourceEventMessageId = trimToNull(headerText(mqMessage, EventHeaders.MESSAGE_ID));
    if (!hasText(sourceEventMessageId)) {
      sourceEventMessageId = command.getRebuildId();
    }

    GraphRebuildStats stats =
        graphSink.rebuildFromMetadata(
            tenant,
            runtime,
            bindingRevision,
            EmbeddingTriggerType.REBUILD,
            sourceEventMessageId,
            gravitinoDBSource.listCatalogs(tenantId),
            gravitinoDBSource.listSchemas(tenantId),
            gravitinoDBSource.listTables(tenantId),
            gravitinoDBSource.listColumns(tenantId),
            gravitinoDBSource.listMetrics(tenantId),
            gravitinoDBSource.listMetricVersions(tenantId),
            gravitinoDBSource.listTags(tenantId),
            gravitinoDBSource.listTagRelations(tenantId),
            gravitinoDBSource.listWordRoots(tenantId),
            gravitinoDBSource.listModifiers(tenantId),
            gravitinoDBSource.listUnits(tenantId),
            gravitinoDBSource.listValueDomains(tenantId));

    int embeddingTasks = embeddingEventPublisher.publishTasks(stats.embeddingTasks());
    log.info(
        "rebuild_consumer_succeeded tenantId={} rebuildId={} revision={} graphUpserts={} embeddingTasks={}",
        tenantId,
        command.getRebuildId(),
        bindingRevision,
        stats.graphUpserts(),
        embeddingTasks);
  }

  private EmbeddingBindingMapper.RuntimeModelRow buildRuntime(RebuildCommand command) {
    if (command.getAiModelId() == null || command.getAiModelId() <= 0) {
      throw new BadRequestException("rebuild command aiModelId is invalid");
    }
    if (!hasText(command.getProviderCode())) {
      throw new BadRequestException("rebuild command providerCode is invalid");
    }
    if (!hasText(command.getProviderModelId())) {
      throw new BadRequestException("rebuild command providerModelId is invalid");
    }
    if (!hasText(command.getApiKeyCiphertext())) {
      throw new BadRequestException("rebuild command apiKey is invalid");
    }
    EmbeddingBindingMapper.RuntimeModelRow runtime = new EmbeddingBindingMapper.RuntimeModelRow();
    runtime.setRevision(command.getBindingRevision());
    runtime.setAiModelId(command.getAiModelId());
    runtime.setTenantId(command.getTenantId());
    runtime.setProviderCode(command.getProviderCode());
    runtime.setProviderModelId(command.getProviderModelId());
    runtime.setEmbeddingDimension(command.getEmbeddingDimension());
    runtime.setBaseUrl(command.getBaseUrl());
    runtime.setApiKey(command.getApiKeyCiphertext());
    return runtime;
  }

  private RebuildCommand parseCommand(String body) {
    if (!hasText(body)) {
      throw new BadRequestException("rebuild command body is empty");
    }
    try {
      RebuildCommand command = openLineageObjectMapper.readValue(body, RebuildCommand.class);
      if (command.getTenantId() == null || !hasText(command.getTenantCode())) {
        throw new BadRequestException("rebuild command payload is invalid");
      }
      return command;
    } catch (JsonProcessingException ex) {
      throw new BadRequestException(ex, "rebuild command payload parse failed");
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
    putIfText(headers, EventHeaders.REBUILD_ID, headerText(mqMessage, EventHeaders.REBUILD_ID));
    putIfText(headers, EventHeaders.TENANT_ID, headerText(mqMessage, EventHeaders.TENANT_ID));
    putIfText(headers, EventHeaders.TENANT_CODE, headerText(mqMessage, EventHeaders.TENANT_CODE));
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

  private String fallbackMessageKey(Map<String, String> headers, String body) {
    String tenantId = trimToNull(headers.get(EventHeaders.TENANT_ID));
    if (tenantId != null) {
      return tenantId;
    }
    try {
      RebuildCommand command = parseCommand(body);
      if (command.getTenantId() != null && command.getTenantId() > 0) {
        return String.valueOf(command.getTenantId());
      }
    } catch (RuntimeException ignored) {
      // Ignore parse failure and fallback to random key.
    }
    return UUID.randomUUID().toString();
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
