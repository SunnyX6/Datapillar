package com.sunny.datapillar.openlineage.dao;

import com.sunny.datapillar.openlineage.model.*;
import com.sunny.datapillar.openlineage.security.TenantContext;
import io.openlineage.client.OpenLineage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件与任务持久化 DAO。
 */
public interface OpenLineageEventDao {

    void createLineageEvent(OpenLineage.RunEvent event, OpenLineageEventEnvelope envelope, TenantContext tenantContext);

    void createDatasetEvent(OpenLineage.DatasetEvent event, OpenLineageEventEnvelope envelope, TenantContext tenantContext);

    void createJobEvent(OpenLineage.JobEvent event, OpenLineageEventEnvelope envelope, TenantContext tenantContext);

    long upsertAsyncTask(TenantContext tenantContext, AsyncTaskCandidate candidate);

    boolean claimTaskForPush(long taskId, String claimToken, LocalDateTime claimUntil);

    List<AsyncTaskRecord> claimRecoverableTasks(AsyncTaskType taskType,
                                                int limit,
                                                String claimToken,
                                                LocalDateTime claimUntil);

    AsyncTaskRecord getTaskById(long taskId);

    long startAttempt(AsyncTaskAttemptRecord record);

    void finishAttempt(long attemptId,
                       String status,
                       LocalDateTime finishedAt,
                       Long latencyMs,
                       String errorType,
                       String errorMessage);

    void markTaskSucceeded(long taskId, String claimToken);

    void markTaskFailed(long taskId,
                        String claimToken,
                        String lastError,
                        LocalDateTime nextRunAt,
                        boolean dead);

    String createBatch(AsyncBatchRecord record);

    void finishBatch(String batchNo,
                     int successCount,
                     int failedCount,
                     String status,
                     LocalDateTime finishedAt);

    LocalDateTime computeNextRunAt(int retryCount, LocalDateTime now);

    String classifyErrorType(Throwable throwable);

    String truncateError(Throwable throwable);

    Duration claimTimeout();
}
