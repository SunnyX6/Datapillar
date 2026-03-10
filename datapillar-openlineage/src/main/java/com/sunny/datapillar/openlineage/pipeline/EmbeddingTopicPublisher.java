package com.sunny.datapillar.openlineage.pipeline;

import com.sunny.datapillar.openlineage.config.OpenLineageRuntimeConfig;
import java.util.Map;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Publisher for embedding topic. */
@Component
public class EmbeddingTopicPublisher {

  private final RocketMQTemplate rocketMQTemplate;
  private final OpenLineageRuntimeConfig runtimeProperties;

  public EmbeddingTopicPublisher(
      RocketMQTemplate rocketMQTemplate, OpenLineageRuntimeConfig runtimeProperties) {
    this.rocketMQTemplate = rocketMQTemplate;
    this.runtimeProperties = runtimeProperties;
  }

  public void send(String body, String messageKey, Map<String, String> headers) {
    Message<String> mqMessage = buildMessage(body, messageKey, headers);
    rocketMQTemplate.syncSendOrderly(
        runtimeProperties.getMq().getTopic().getEmbedding(),
        mqMessage,
        messageKey,
        runtimeProperties.getMq().getSendTimeoutMillis());
  }

  public void retry(String body, String messageKey, Map<String, String> headers, int delaySeconds) {
    Message<String> mqMessage = buildMessage(body, messageKey, headers);
    rocketMQTemplate.syncSendDelayTimeSeconds(
        runtimeProperties.getMq().getTopic().getEmbedding(), mqMessage, delaySeconds);
  }

  private Message<String> buildMessage(
      String body, String messageKey, Map<String, String> headers) {
    MessageBuilder<String> builder =
        MessageBuilder.withPayload(body).setHeader(RocketMQHeaders.KEYS, messageKey);
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        if (entry.getKey() != null && StringUtils.hasText(entry.getValue())) {
          builder.setHeader(entry.getKey(), entry.getValue());
        }
      }
    }
    return builder.build();
  }
}
