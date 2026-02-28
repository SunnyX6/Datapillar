package com.sunny.datapillar.openlineage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.openlineage.async.AsyncTaskDispatcher;
import com.sunny.datapillar.openlineage.dao.OpenLineageDao;
import com.sunny.datapillar.openlineage.dao.OpenLineageEventDao;
import com.sunny.datapillar.openlineage.model.AsyncTaskCandidate;
import com.sunny.datapillar.openlineage.model.AsyncTaskType;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.OpenLineageUpdateResult;
import com.sunny.datapillar.openlineage.model.TenantSourceType;
import com.sunny.datapillar.openlineage.security.TenantContext;
import com.sunny.datapillar.openlineage.service.impl.OpenLineageServiceImpl;
import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineageClientUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenLineageServiceTest {

    @Mock
    private OpenLineageDao openLineageDao;

    @Mock
    private OpenLineageEventDao openLineageEventDao;

    @Mock
    private AsyncTaskDispatcher asyncTaskDispatcher;

    private final ObjectMapper mapper = OpenLineageClientUtils.newObjectMapper();

    @Test
    void shouldWriteEventAndDispatchTask() throws Exception {
        Executor directExecutor = Runnable::run;
        OpenLineageServiceImpl service = new OpenLineageServiceImpl(
                openLineageDao,
                openLineageEventDao,
                asyncTaskDispatcher,
                directExecutor);

        OpenLineageEventEnvelope envelope = parseRunEvent();
        TenantContext tenantContext = new TenantContext(1001L, "t-1001", "Tenant1001", TenantSourceType.GRAVITINO);

        AsyncTaskCandidate candidate = AsyncTaskCandidate.builder()
                .taskType(AsyncTaskType.EMBEDDING)
                .resourceType("TABLE")
                .resourceId("table-1")
                .contentHash("hash-1")
                .modelFingerprint("builtin:embedding:v1")
                .payload("sales.orders")
                .build();

        OpenLineageUpdateResult updateResult = OpenLineageUpdateResult.builder().addCandidate(candidate).build();

        when(openLineageDao.updateDatapillarModel(any(OpenLineage.RunEvent.class), eq(envelope), eq(tenantContext)))
                .thenReturn(updateResult);
        when(openLineageEventDao.upsertAsyncTask(eq(tenantContext), eq(candidate))).thenReturn(10L);
        when(openLineageEventDao.claimTimeout()).thenReturn(Duration.ofSeconds(120));
        when(openLineageEventDao.claimTaskForPush(eq(10L), any(String.class), any(LocalDateTime.class))).thenReturn(true);

        OpenLineage.RunEvent runEvent = (OpenLineage.RunEvent) envelope.getEvent();
        Assertions.assertDoesNotThrow(() -> service.createAsync(runEvent, envelope, tenantContext).join());

        verify(openLineageDao).createLineageEvent(runEvent, envelope, tenantContext);
        verify(openLineageDao).updateDatapillarModel(runEvent, envelope, tenantContext);
        verify(openLineageEventDao).upsertAsyncTask(tenantContext, candidate);
        verify(asyncTaskDispatcher).dispatch(eq(AsyncTaskType.EMBEDDING), eq(10L), any(String.class));
    }

    private OpenLineageEventEnvelope parseRunEvent() throws Exception {
        JsonNode node = mapper.readTree("""
                {
                  "eventTime": "2026-02-28T00:00:00Z",
                  "eventType": "START",
                  "producer": "https://gravitino.apache.org",
                  "run": {"runId": "11111111-1111-1111-1111-111111111111"},
                  "job": {"namespace": "gravitino://tenant/1001", "name": "gravitino.table.create"},
                  "inputs": [{
                    "namespace": "gravitino://tenant/1001/OneMeta/OneDS",
                    "name": "sales.orders",
                    "facets": {"gravitino": {"tenantId": 1001, "tenantCode": "t-1001", "tenantName": "Tenant1001"}}
                  }],
                  "outputs": []
                }
                """);
        OpenLineage.RunEvent event = mapper.treeToValue(node, OpenLineage.RunEvent.class);
        return OpenLineageEventEnvelope.fromRunEvent(event, node);
    }
}
