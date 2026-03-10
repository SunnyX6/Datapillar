package com.sunny.datapillar.openlineage.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.openlineage.config.OpenLineageRuntimeConfig;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.sink.VectorSink;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Publisher that sends full embedding tasks to MQ. */
@Component
public class EmbeddingEventPublisher {

  private final EmbeddingTopicPublisher embeddingTopicPublisher;
  private final VectorSink vectorSink;
  private final OpenLineageRuntimeConfig runtimeProperties;
  private final ObjectMapper openLineageObjectMapper;

  public EmbeddingEventPublisher(
      EmbeddingTopicPublisher embeddingTopicPublisher,
      VectorSink vectorSink,
      OpenLineageRuntimeConfig runtimeProperties,
      @Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper) {
    this.embeddingTopicPublisher = embeddingTopicPublisher;
    this.vectorSink = vectorSink;
    this.runtimeProperties = runtimeProperties;
    this.openLineageObjectMapper = openLineageObjectMapper;
  }

  public int publishTasks(List<EmbeddingTaskPayload> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return 0;
    }

    int queued = 0;
    for (EmbeddingTaskPayload task : tasks) {
      if (task == null) {
        continue;
      }
      validateTask(task);

      String messageId = UUID.randomUUID().toString();
      Map<String, String> headers = buildHeaders(messageId, task);
      embeddingTopicPublisher.send(toJson(task), String.valueOf(task.getTenantId()), headers);
      queued++;
    }
    return queued;
  }

  public int enqueueTenantRefresh(
      Tenant tenant,
      EmbeddingBindingMapper.RuntimeModelRow runtime,
      Long targetRevision,
      EmbeddingTriggerType trigger) {
    if (tenant == null || tenant.getTenantId() == null || tenant.getTenantId() <= 0) {
      throw new InternalException("tenantId is invalid");
    }
    if (!StringUtils.hasText(tenant.getTenantCode())) {
      throw new InternalException("tenantCode is invalid");
    }
    validateRuntime(runtime);
    if (targetRevision == null || targetRevision <= 0) {
      throw new InternalException("targetRevision is invalid");
    }

    Long tenantId = tenant.getTenantId();
    int batchSize = runtimeProperties.getRebuild().getEmbeddingBatchSize();
    int offset = 0;
    int total = 0;
    while (true) {
      List<EmbeddingTaskPayload> page =
          vectorSink.listTenantTasks(tenant, runtime, targetRevision, trigger, batchSize, offset);
      if (page.isEmpty()) {
        break;
      }
      total += publishTasks(page);
      offset += page.size();
    }
    return total;
  }

  private void validateTask(EmbeddingTaskPayload task) {
    if (task.getTenantId() == null || task.getTenantId() <= 0) {
      throw new InternalException("embedding task tenantId is invalid");
    }
    if (!StringUtils.hasText(task.getTenantCode())) {
      throw new InternalException("embedding task tenantCode is invalid");
    }
    if (!StringUtils.hasText(task.getResourceId()) || !StringUtils.hasText(task.getContent())) {
      throw new InternalException("embedding task payload is invalid");
    }
    if (task.getTargetRevision() == null || task.getTargetRevision() <= 0) {
      throw new InternalException("embedding task targetRevision is invalid");
    }
    if (task.getAiModelId() == null || task.getAiModelId() <= 0) {
      throw new InternalException("embedding task aiModelId is invalid");
    }
    if (!StringUtils.hasText(task.getProviderCode())) {
      throw new InternalException("embedding task providerCode is invalid");
    }
    if (!StringUtils.hasText(task.getProviderModelId())) {
      throw new InternalException("embedding task providerModelId is invalid");
    }
    if (!StringUtils.hasText(task.getApiKeyCiphertext())) {
      throw new InternalException("embedding task apiKey is invalid");
    }
  }

  private void validateRuntime(EmbeddingBindingMapper.RuntimeModelRow runtime) {
    if (runtime == null) {
      throw new InternalException("embedding runtime is missing");
    }
    if (runtime.getAiModelId() == null || runtime.getAiModelId() <= 0) {
      throw new InternalException("embedding runtime aiModelId is invalid");
    }
    if (!StringUtils.hasText(runtime.getProviderCode())) {
      throw new InternalException("embedding runtime providerCode is invalid");
    }
    if (!StringUtils.hasText(runtime.getProviderModelId())) {
      throw new InternalException("embedding runtime providerModelId is invalid");
    }
    if (!StringUtils.hasText(runtime.getApiKey())) {
      throw new InternalException("embedding runtime apiKey is invalid");
    }
  }

  private Map<String, String> buildHeaders(String messageId, EmbeddingTaskPayload task) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(EventHeaders.MESSAGE_ID, messageId);
    headers.put(EventHeaders.TENANT_ID, String.valueOf(task.getTenantId()));
    headers.put(
        EventHeaders.TENANT_CODE,
        task.getTenantCode() == null ? null : task.getTenantCode().trim());
    headers.put(EventHeaders.ATTEMPT, "0");
    headers.put(EventHeaders.ENQUEUED_AT, String.valueOf(System.currentTimeMillis()));
    headers.put(EventHeaders.SCHEMA_VERSION, EventHeaders.EMBEDDING_SCHEMA);
    if (task.getTrigger() != null) {
      headers.put(EventHeaders.TRIGGER, task.getTrigger().name());
    }
    return headers;
  }

  private String toJson(EmbeddingTaskPayload task) {
    try {
      return openLineageObjectMapper.writeValueAsString(task);
    } catch (JsonProcessingException ex) {
      throw new InternalException(ex, "Serialize embedding task failed");
    }
  }
}
