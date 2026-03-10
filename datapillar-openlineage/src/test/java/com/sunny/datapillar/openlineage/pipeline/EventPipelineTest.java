package com.sunny.datapillar.openlineage.pipeline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.sink.GraphSink;
import com.sunny.datapillar.openlineage.source.FlinkSource;
import com.sunny.datapillar.openlineage.source.GravitinoOpenlineageSource;
import com.sunny.datapillar.openlineage.source.HiveSource;
import com.sunny.datapillar.openlineage.source.OpenLineageSourceModels;
import com.sunny.datapillar.openlineage.source.SparkSource;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventPipelineTest {

  @Mock private GraphSink graphSink;
  @Mock private EmbeddingBindingMapper embeddingBindingMapper;
  @Mock private EmbeddingEventPublisher embeddingEventPublisher;
  @Mock private EventTopicPublisher eventTopicPublisher;
  @Mock private DeadLetterPublisher deadLetterPublisher;
  @Mock private MqRetryPolicy retryPolicy;
  @Mock private EventTenantResolver eventTenantResolver;
  @Mock private GravitinoOpenlineageSource gravitinoOpenlineageSource;
  @Mock private HiveSource hiveSource;
  @Mock private FlinkSource flinkSource;
  @Mock private SparkSource sparkSource;

  @Test
  void consume_shouldPublishRealtimeTasksReturnedByGraphSink() {
    EventPipeline pipeline = createPipeline();

    Tenant tenant = new Tenant();
    tenant.setTenantId(3003L);
    tenant.setTenantCode("t-3003");

    OpenLineageSourceModels models = new OpenLineageSourceModels();
    EmbeddingTaskPayload task = new EmbeddingTaskPayload();
    task.setTenantId(3003L);
    task.setTenantCode("t-3003");
    task.setResourceId("table-orders");
    task.setResourceType("Table");
    task.setContent("orders");
    task.setTargetRevision(8L);
    task.setTrigger(EmbeddingTriggerType.REALTIME);
    task.setSourceEventMessageId("ev-1");
    task.setAiModelId(11L);
    task.setProviderCode("OPENAI");
    task.setProviderModelId("text-embedding-3-small");
    task.setEmbeddingDimension(1536);
    task.setApiKeyCiphertext("ENCv1:test");
    task.setBaseUrl("https://api.openai.com/v1");

    EmbeddingBindingMapper.RuntimeModelRow runtime = new EmbeddingBindingMapper.RuntimeModelRow();
    runtime.setRevision(8L);
    runtime.setAiModelId(11L);
    runtime.setTenantId(3003L);
    runtime.setModelType("embeddings");
    runtime.setStatus("ACTIVE");
    runtime.setProviderCode("OPENAI");
    runtime.setProviderModelId("text-embedding-3-small");
    runtime.setEmbeddingDimension(1536);
    runtime.setApiKey("ENCv1:test");
    runtime.setBaseUrl("https://api.openai.com/v1");

    when(gravitinoOpenlineageSource.supports(any())).thenReturn(true);
    when(gravitinoOpenlineageSource.readModels(eq(3003L), any())).thenReturn(models);
    when(eventTenantResolver.resolve(eq(3003L), eq("t-3003"), eq(models))).thenReturn(tenant);
    when(embeddingBindingMapper.selectDwRuntimeByTenant(3003L, "DW", 0L))
        .thenReturn(List.of(runtime));
    when(graphSink.apply(
            eq(tenant),
            eq(models),
            eq(runtime),
            eq(8L),
            eq(EmbeddingTriggerType.REALTIME),
            eq("ev-1")))
        .thenReturn(List.of(task));

    MessageExt message = new MessageExt();
    message.setBody(
        """
        {
          "producer":"gravitino",
          "job":{"name":"gravitino.table"}
        }
        """
            .getBytes(StandardCharsets.UTF_8));
    message.putUserProperty(EventHeaders.TENANT_ID, "3003");
    message.putUserProperty(EventHeaders.TENANT_CODE, "t-3003");
    message.putUserProperty(EventHeaders.MESSAGE_ID, "ev-1");

    pipeline.consume(message);

    verify(embeddingEventPublisher).publishTasks(eq(List.of(task)));
  }

  private EventPipeline createPipeline() {
    return new EventPipeline(
        new ObjectMapper(),
        graphSink,
        embeddingBindingMapper,
        embeddingEventPublisher,
        eventTopicPublisher,
        deadLetterPublisher,
        retryPolicy,
        eventTenantResolver,
        gravitinoOpenlineageSource,
        hiveSource,
        flinkSource,
        sparkSource);
  }
}
