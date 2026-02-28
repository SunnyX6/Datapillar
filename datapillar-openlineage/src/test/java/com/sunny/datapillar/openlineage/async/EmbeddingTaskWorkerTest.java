package com.sunny.datapillar.openlineage.async;

import com.sunny.datapillar.openlineage.dao.OpenLineageEventDao;
import com.sunny.datapillar.openlineage.dao.OpenLineageGraphDao;
import com.sunny.datapillar.openlineage.model.AsyncTaskRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingTaskWorkerTest {

    private final OpenLineageEventDao eventDao = mock(OpenLineageEventDao.class);
    private final OpenLineageGraphDao graphDao = mock(OpenLineageGraphDao.class);

    private ExecutorService executorService;
    private EmbeddingTaskWorker worker;

    @BeforeEach
    void setUp() {
        executorService = Executors.newSingleThreadExecutor();
        worker = new EmbeddingTaskWorker(eventDao, graphDao, executorService, 8, 8, 300);
    }

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.stop();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldMarkTaskFailedWhenEmbeddingWriteFails() {
        AsyncTaskRecord record = new AsyncTaskRecord();
        record.setId(1L);
        record.setTaskType(AsyncTaskType.EMBEDDING.name());
        record.setTenantId(1001L);
        record.setResourceType("TABLE");
        record.setResourceId("table-1");
        record.setStatus("RUNNING");
        record.setClaimToken("claim-1");
        record.setRetryCount(0);
        record.setMaxRetry(5);
        record.setModelFingerprint("builtin:embedding:v1");

        when(eventDao.getTaskById(1L)).thenReturn(record);
        when(eventDao.startAttempt(any())).thenReturn(99L);
        when(eventDao.classifyErrorType(any())).thenReturn("STATE_ERROR");
        when(eventDao.truncateError(any())).thenReturn("STATE_ERROR: 向量任务缺少可用内容");
        when(eventDao.computeNextRunAt(eq(1), any(LocalDateTime.class))).thenReturn(LocalDateTime.now().plusMinutes(1));
        when(eventDao.claimRecoverableTasks(any(), any(Integer.class), any(String.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(graphDao.fetchResourceContent(1001L, "TABLE", "table-1")).thenReturn("");

        worker.start();
        worker.submit(1L, "claim-1");

        verify(eventDao, timeout(3000)).markTaskFailed(eq(1L), eq("claim-1"), any(String.class), any(LocalDateTime.class), eq(false));
        verify(eventDao, timeout(3000)).finishAttempt(eq(99L), eq("FAILED"), any(LocalDateTime.class), any(Long.class), any(String.class), any(String.class));
    }
}
