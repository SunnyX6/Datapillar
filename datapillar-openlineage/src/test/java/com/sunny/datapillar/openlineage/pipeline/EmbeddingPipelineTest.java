package com.sunny.datapillar.openlineage.pipeline;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.openlineage.sink.VectorSink;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.web.security.TenantApiKeyDecryptor;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingPipelineTest {

  @Mock private TenantApiKeyDecryptor tenantApiKeyDecryptor;
  @Mock private VectorSink vectorSink;
  @Mock private EmbeddingTopicPublisher embeddingTopicPublisher;
  @Mock private DeadLetterPublisher deadLetterPublisher;
  @Mock private MqRetryPolicy retryPolicy;

  @Test
  void consume_shouldRejectWhenTenantHeaderMissing() {
    EmbeddingPipeline pipeline = createPipeline();
    MessageExt message = new MessageExt();
    message.setBody(validTaskJson().getBytes(StandardCharsets.UTF_8));
    message.putUserProperty(EventHeaders.TENANT_CODE, "t-3003");

    assertThrows(BadRequestException.class, () -> pipeline.consume(message));
    verify(vectorSink, never()).writeResult(anyLong(), any(), any(), any(), anyLong());
  }

  @Test
  void consume_shouldRejectWhenTaskTenantMismatch() {
    EmbeddingPipeline pipeline = createPipeline();
    MessageExt message = new MessageExt();
    message.setBody(
        validTaskJson()
            .replace("\"tenantId\":3003", "\"tenantId\":4004")
            .getBytes(StandardCharsets.UTF_8));
    message.putUserProperty(EventHeaders.TENANT_ID, "3003");
    message.putUserProperty(EventHeaders.TENANT_CODE, "t-3003");

    assertThrows(BadRequestException.class, () -> pipeline.consume(message));
    verify(vectorSink, never()).writeResult(anyLong(), any(), any(), any(), anyLong());
  }

  private EmbeddingPipeline createPipeline() {
    return new EmbeddingPipeline(
        tenantApiKeyDecryptor,
        vectorSink,
        embeddingTopicPublisher,
        deadLetterPublisher,
        retryPolicy,
        new ObjectMapper());
  }

  private String validTaskJson() {
    return """
        {
          "tenantId":3003,
          "tenantCode":"t-3003",
          "resourceId":"table:orders",
          "resourceType":"Table",
          "content":"orders table",
          "targetRevision":2,
          "trigger":"REALTIME",
          "sourceEventMessageId":"ev-1",
          "aiModelId":10,
          "providerCode":"OPENAI",
          "providerModelId":"text-embedding-3-small",
          "embeddingDimension":1536,
          "baseUrl":"https://api.openai.com/v1",
          "apiKeyCiphertext":"ENCv1:cipher-api-key"
        }
        """;
  }
}
