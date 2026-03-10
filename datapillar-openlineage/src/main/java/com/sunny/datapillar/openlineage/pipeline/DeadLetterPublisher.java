package com.sunny.datapillar.openlineage.pipeline;

import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.openlineage.config.OpenLineageRuntimeConfig;
import com.sunny.datapillar.openlineage.source.event.DeadLetterEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/** Publisher for custom DLQ topics. */
@Component
public class DeadLetterPublisher {

  private final RocketMQTemplate rocketMQTemplate;
  private final OpenLineageRuntimeConfig runtimeProperties;

  public DeadLetterPublisher(
      RocketMQTemplate rocketMQTemplate, OpenLineageRuntimeConfig runtimeProperties) {
    this.rocketMQTemplate = rocketMQTemplate;
    this.runtimeProperties = runtimeProperties;
  }

  public void publishEvent(
      String reason, int attempt, String body, Map<String, String> originalHeaders) {
    publish(
        runtimeProperties.getMq().getTopic().getEventsDlq(),
        runtimeProperties.getMq().getGroup().getGraphConsumer(),
        runtimeProperties.getMq().getTopic().getEvents(),
        reason,
        attempt,
        body,
        originalHeaders);
  }

  public void publishEmbedding(
      String reason, int attempt, String body, Map<String, String> originalHeaders) {
    publish(
        runtimeProperties.getMq().getTopic().getEmbeddingDlq(),
        runtimeProperties.getMq().getGroup().getEmbeddingConsumer(),
        runtimeProperties.getMq().getTopic().getEmbedding(),
        reason,
        attempt,
        body,
        originalHeaders);
  }

  public void publishRebuild(
      String reason, int attempt, String body, Map<String, String> originalHeaders) {
    publish(
        runtimeProperties.getMq().getTopic().getRebuildCommandDlq(),
        runtimeProperties.getMq().getGroup().getRebuildConsumer(),
        runtimeProperties.getMq().getTopic().getRebuildCommand(),
        reason,
        attempt,
        body,
        originalHeaders);
  }

  private void publish(
      String topic,
      String consumerGroup,
      String sourceTopic,
      String reason,
      int attempt,
      String body,
      Map<String, String> originalHeaders) {
    if (body == null) {
      throw new InternalException("DLQ payload body is null");
    }

    DeadLetterEvent event = new DeadLetterEvent();
    event.setSourceTopic(sourceTopic);
    event.setConsumerGroup(consumerGroup);
    event.setReason(reason);
    event.setAttempt(Math.max(0, attempt));
    event.setFailedAt(System.currentTimeMillis());
    event.setOriginalBody(body);
    event.setOriginalHeaders(
        originalHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(originalHeaders));

    rocketMQTemplate.syncSend(topic, event);
  }
}
