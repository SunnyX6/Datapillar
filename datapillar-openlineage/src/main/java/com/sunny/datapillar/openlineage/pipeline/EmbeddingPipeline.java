package com.sunny.datapillar.openlineage.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.openlineage.sink.VectorSink;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.web.security.TenantApiKeyDecryptor;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.util.StringUtils;

/** Embedding pipeline for embedding consume flow. */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = "${openlineage.mq.topic.embedding:dp.openlineage.embedding}",
    consumerGroup = "${openlineage.mq.group.embedding-consumer:ol-embedding-consumer}",
    consumeMode = ConsumeMode.ORDERLY,
    messageModel = MessageModel.CLUSTERING)
public class EmbeddingPipeline implements RocketMQListener<MessageExt> {

  private final TenantApiKeyDecryptor tenantApiKeyDecryptor;
  private final VectorSink vectorSink;
  private final EmbeddingTopicPublisher embeddingTopicPublisher;
  private final DeadLetterPublisher deadLetterPublisher;
  private final MqRetryPolicy retryPolicy;
  private final ObjectMapper openLineageObjectMapper;

  public EmbeddingPipeline(
      TenantApiKeyDecryptor tenantApiKeyDecryptor,
      VectorSink vectorSink,
      EmbeddingTopicPublisher embeddingTopicPublisher,
      DeadLetterPublisher deadLetterPublisher,
      MqRetryPolicy retryPolicy,
      @Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper) {
    this.tenantApiKeyDecryptor = tenantApiKeyDecryptor;
    this.vectorSink = vectorSink;
    this.embeddingTopicPublisher = embeddingTopicPublisher;
    this.deadLetterPublisher = deadLetterPublisher;
    this.retryPolicy = retryPolicy;
    this.openLineageObjectMapper = openLineageObjectMapper;
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
          "embedding_consumer_non_retryable tenantId={} messageId={} attempt={} reason={}",
          headers.get(EventHeaders.TENANT_ID),
          headers.get(EventHeaders.MESSAGE_ID),
          currentAttempt,
          ex.getMessage());
      deadLetterPublisher.publishEmbedding(ex.getMessage(), currentAttempt, body, headers);
    } catch (Throwable ex) {
      int nextAttempt = currentAttempt + 1;
      if (retryPolicy.shouldRetry(nextAttempt)) {
        int delaySeconds = retryPolicy.nextDelaySeconds(currentAttempt);
        headers.put(EventHeaders.ATTEMPT, String.valueOf(nextAttempt));
        headers.put(EventHeaders.ENQUEUED_AT, String.valueOf(System.currentTimeMillis()));
        embeddingTopicPublisher.retry(body, messageKey, headers, delaySeconds);
        log.warn(
            "embedding_consumer_retry tenantId={} messageId={} attempt={} delaySeconds={} reason={}",
            headers.get(EventHeaders.TENANT_ID),
            headers.get(EventHeaders.MESSAGE_ID),
            nextAttempt,
            delaySeconds,
            ex.getMessage());
      } else {
        deadLetterPublisher.publishEmbedding(ex.getMessage(), currentAttempt, body, headers);
        log.error(
            "embedding_consumer_dlq tenantId={} messageId={} attempt={} reason={}",
            headers.get(EventHeaders.TENANT_ID),
            headers.get(EventHeaders.MESSAGE_ID),
            currentAttempt,
            ex.getMessage(),
            ex);
      }
    }
  }

  public void consume(MessageExt mqMessage) {
    Long tenantId = headerLong(mqMessage, EventHeaders.TENANT_ID);
    if (tenantId == null || tenantId <= 0) {
      throw new BadRequestException("embedding message tenantId is invalid");
    }
    String tenantCode = headerText(mqMessage, EventHeaders.TENANT_CODE);
    if (!hasText(tenantCode)) {
      throw new BadRequestException("embedding message tenantCode is invalid");
    }

    EmbeddingTaskPayload task = parseTask(messageBody(mqMessage));
    validateTask(task);
    validateTaskTenant(tenantId, tenantCode, task);

    String apiKeyPlaintext =
        tenantApiKeyDecryptor.decryptModelApiKey(
            task.getTenantCode().trim(), task.getApiKeyCiphertext());
    List<Double> vector = embed(task, apiKeyPlaintext);
    vectorSink.writeResult(
        task.getTenantId(),
        task.getResourceId(),
        vector,
        task.getProviderCode(),
        task.getTargetRevision());
  }

  private EmbeddingTaskPayload parseTask(String body) {
    if (!hasText(body)) {
      throw new BadRequestException("embedding message body is empty");
    }
    try {
      EmbeddingTaskPayload task =
          openLineageObjectMapper.readValue(body, EmbeddingTaskPayload.class);
      if (task == null) {
        throw new BadRequestException("embedding task payload is invalid");
      }
      return task;
    } catch (JsonProcessingException ex) {
      throw new BadRequestException(ex, "embedding payload parse failed");
    }
  }

  private void validateTask(EmbeddingTaskPayload task) {
    if (task.getTenantId() == null || task.getTenantId() <= 0) {
      throw new BadRequestException("embedding task tenantId is invalid");
    }
    if (!hasText(task.getTenantCode())) {
      throw new BadRequestException("embedding task tenantCode is invalid");
    }
    if (!hasText(task.getResourceId()) || !hasText(task.getContent())) {
      throw new BadRequestException("embedding task payload is invalid");
    }
    if (task.getTargetRevision() == null || task.getTargetRevision() <= 0) {
      throw new BadRequestException("embedding task targetRevision is invalid");
    }
    if (task.getAiModelId() == null || task.getAiModelId() <= 0) {
      throw new BadRequestException("embedding task aiModelId is invalid");
    }
    if (!hasText(task.getProviderCode())) {
      throw new BadRequestException("embedding task providerCode is invalid");
    }
    if (!hasText(task.getProviderModelId())) {
      throw new BadRequestException("embedding task providerModelId is invalid");
    }
    if (!hasText(task.getApiKeyCiphertext())) {
      throw new BadRequestException("embedding task apiKey is invalid");
    }
  }

  private void validateTaskTenant(Long tenantId, String tenantCode, EmbeddingTaskPayload task) {
    if (!tenantId.equals(task.getTenantId())) {
      throw new BadRequestException("embedding task tenantId mismatch");
    }
    if (!tenantCode.trim().equals(task.getTenantCode().trim())) {
      throw new BadRequestException("embedding task tenantCode mismatch");
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

  private String fallbackMessageKey(Map<String, String> headers) {
    String tenantId = trimToNull(headers.get(EventHeaders.TENANT_ID));
    String messageId = trimToNull(headers.get(EventHeaders.MESSAGE_ID));
    if (tenantId != null && messageId != null) {
      return tenantId + "|" + messageId;
    }
    return UUID.randomUUID().toString();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private List<Double> embed(EmbeddingTaskPayload task, String apiKeyPlaintext) {
    if (!StringUtils.hasText(apiKeyPlaintext)) {
      throw new InternalException("Model apiKey is empty");
    }
    if (!StringUtils.hasText(task.getContent())) {
      throw new InternalException("Embedding content is empty");
    }
    try {
      EmbeddingModel model = buildOpenAiEmbeddingModel(task, apiKeyPlaintext);
      Embedding embedding = model.embed(task.getContent()).content();
      return embedding.vectorAsList().stream().map(Float::doubleValue).toList();
    } catch (RuntimeException ex) {
      throw new InternalException(ex, "Embedding invocation failed");
    }
  }

  private EmbeddingModel buildOpenAiEmbeddingModel(
      EmbeddingTaskPayload task, String apiKeyPlaintext) {
    OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder =
        OpenAiEmbeddingModel.builder()
            .apiKey(apiKeyPlaintext)
            .modelName(task.getProviderModelId())
            .timeout(Duration.ofSeconds(30))
            .maxRetries(2);
    if (task.getEmbeddingDimension() != null && task.getEmbeddingDimension() > 0) {
      builder.dimensions(task.getEmbeddingDimension());
    }
    if (StringUtils.hasText(task.getBaseUrl())) {
      builder.baseUrl(task.getBaseUrl());
    }
    return builder.build();
  }

  private String trimToNull(String value) {
    if (!hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
