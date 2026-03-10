package com.sunny.datapillar.openlineage.pipeline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.openlineage.sink.GraphSink;
import com.sunny.datapillar.openlineage.source.GravitinoDBSource;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.source.event.RebuildCommand;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RebuildCommandPipelineTest {

  @Mock private GraphSink graphSink;
  @Mock private GravitinoDBSource gravitinoDBSource;
  @Mock private EmbeddingEventPublisher embeddingEventPublisher;
  @Mock private RebuildCommandTopicPublisher rebuildCommandTopicPublisher;
  @Mock private DeadLetterPublisher deadLetterPublisher;
  @Mock private MqRetryPolicy retryPolicy;

  @Test
  void consume_shouldRebuildGraphAndPublishEmbeddingTasks() {
    RebuildCommandPipeline pipeline =
        new RebuildCommandPipeline(
            graphSink,
            gravitinoDBSource,
            embeddingEventPublisher,
            rebuildCommandTopicPublisher,
            deadLetterPublisher,
            retryPolicy,
            new ObjectMapper());

    when(gravitinoDBSource.listCatalogs(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listSchemas(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listTables(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listColumns(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listMetrics(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listMetricVersions(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listTags(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listTagRelations(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listWordRoots(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listModifiers(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listUnits(3003L)).thenReturn(List.of());
    when(gravitinoDBSource.listValueDomains(3003L)).thenReturn(List.of());

    EmbeddingTaskPayload task = new EmbeddingTaskPayload();
    task.setTenantId(3003L);
    task.setTenantCode("t-3003");
    task.setResourceId("table-orders");
    task.setResourceType("Table");
    task.setContent("orders");
    task.setTargetRevision(8L);
    task.setTrigger(EmbeddingTriggerType.REBUILD);
    task.setSourceEventMessageId("rb-1");
    task.setAiModelId(11L);
    task.setProviderCode("OPENAI");
    task.setProviderModelId("text-embedding-3-small");
    task.setEmbeddingDimension(1536);
    task.setBaseUrl("https://api.openai.com/v1");
    task.setApiKeyCiphertext("ENCv1:test-key");

    when(graphSink.rebuildFromMetadata(
            any(),
            any(),
            eq(8L),
            eq(EmbeddingTriggerType.REBUILD),
            eq("rb-1"),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList()))
        .thenReturn(new GraphRebuildStats(1, List.of(task)));

    RebuildCommand command = new RebuildCommand();
    command.setRebuildId("rb-1");
    command.setTenantId(3003L);
    command.setTenantCode("t-3003");
    command.setAiModelId(11L);
    command.setBindingRevision(8L);
    command.setProviderCode("OPENAI");
    command.setProviderModelId("text-embedding-3-small");
    command.setEmbeddingDimension(1536);
    command.setBaseUrl("https://api.openai.com/v1");
    command.setApiKeyCiphertext("ENCv1:test-key");
    command.setRequestedBy(101L);
    command.setRequestedAt(System.currentTimeMillis());

    MessageExt message = new MessageExt();
    message.setBody(toJson(command).getBytes(StandardCharsets.UTF_8));
    message.putUserProperty(EventHeaders.MESSAGE_ID, "rb-1");
    message.putUserProperty(EventHeaders.REBUILD_ID, "rb-1");
    message.putUserProperty(EventHeaders.TENANT_ID, "3003");
    message.putUserProperty(EventHeaders.TENANT_CODE, "t-3003");

    pipeline.consume(message);

    verify(graphSink)
        .rebuildFromMetadata(
            any(),
            any(),
            eq(8L),
            eq(EmbeddingTriggerType.REBUILD),
            eq("rb-1"),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            anyList());
    verify(embeddingEventPublisher).publishTasks(eq(List.of(task)));
  }

  private String toJson(RebuildCommand command) {
    try {
      return new ObjectMapper().writeValueAsString(command);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
