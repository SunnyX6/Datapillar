package com.sunny.datapillar.openlineage.dao.impl;

import com.sunny.datapillar.openlineage.dao.OpenLineageEventDao;
import com.sunny.datapillar.openlineage.dao.mapper.OpenLineageEventMapper;
import com.sunny.datapillar.openlineage.exception.OpenLineageWriteException;
import com.sunny.datapillar.openlineage.model.AsyncBatchRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskAttemptRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskCandidate;
import com.sunny.datapillar.openlineage.model.AsyncTaskRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskType;
import com.sunny.datapillar.openlineage.model.LineageEventRecord;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.security.TenantContext;
import io.openlineage.client.OpenLineage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * OpenLineage 事件与异步任务 DAO 实现。
 */
@Repository
public class OpenLineageEventDaoImpl implements OpenLineageEventDao {

    private final OpenLineageEventMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final Duration claimTimeout;

    public OpenLineageEventDaoImpl(OpenLineageEventMapper mapper,
                                   TransactionTemplate transactionTemplate,
                                   @Value("${openlineage.worker.claim-timeout-seconds:120}") int claimTimeoutSeconds) {
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
        this.claimTimeout = Duration.ofSeconds(Math.max(30, claimTimeoutSeconds));
    }

    @Override
    public void createLineageEvent(OpenLineage.RunEvent event,
                                   OpenLineageEventEnvelope envelope,
                                   TenantContext tenantContext) {
        insertLineageEvent(envelope, tenantContext, envelope.runEventType());
    }

    @Override
    public void createDatasetEvent(OpenLineage.DatasetEvent event,
                                   OpenLineageEventEnvelope envelope,
                                   TenantContext tenantContext) {
        insertLineageEvent(envelope, tenantContext, null);
    }

    @Override
    public void createJobEvent(OpenLineage.JobEvent event,
                               OpenLineageEventEnvelope envelope,
                               TenantContext tenantContext) {
        insertLineageEvent(envelope, tenantContext, null);
    }

    @Override
    public long upsertAsyncTask(TenantContext tenantContext, AsyncTaskCandidate candidate) {
        if (tenantContext == null || candidate == null || candidate.taskType() == null) {
            throw new OpenLineageWriteException("异步任务入库参数缺失");
        }

        AsyncTaskRecord record = new AsyncTaskRecord();
        record.setTaskType(candidate.taskType().name());
        record.setTenantId(tenantContext.tenantId());
        record.setTenantCode(requireText(tenantContext.tenantCode(), "tenantCode"));
        record.setResourceType(requireText(candidate.resourceType(), "resourceType"));
        record.setResourceId(requireText(candidate.resourceId(), "resourceId"));
        record.setContentHash(requireText(candidate.contentHash(), "contentHash"));
        record.setModelFingerprint(requireText(candidate.modelFingerprint(), "modelFingerprint"));
        record.setStatus("PENDING");
        record.setPriority(100);
        record.setRetryCount(0);
        record.setMaxRetry(5);
        record.setNextRunAt(LocalDateTime.now(ZoneOffset.UTC));

        mapper.upsertAsyncTask(record);
        Long id = mapper.selectLastInsertId();
        if (id == null || id <= 0) {
            throw new OpenLineageWriteException("异步任务入库失败，未返回任务ID");
        }
        return id;
    }

    @Override
    public boolean claimTaskForPush(long taskId, String claimToken, LocalDateTime claimUntil) {
        if (taskId <= 0 || claimToken == null || claimToken.isBlank() || claimUntil == null) {
            return false;
        }
        return mapper.claimTaskForPush(taskId, claimToken, claimUntil) > 0;
    }

    @Override
    public List<AsyncTaskRecord> claimRecoverableTasks(AsyncTaskType taskType,
                                                        int limit,
                                                        String claimToken,
                                                        LocalDateTime claimUntil) {
        if (taskType == null || limit <= 0 || claimToken == null || claimToken.isBlank() || claimUntil == null) {
            return List.of();
        }

        List<AsyncTaskRecord> claimed = transactionTemplate.execute(status -> {
            List<Long> ids = mapper.selectRecoverableTaskIdsForUpdate(taskType.name(), limit);
            if (ids == null || ids.isEmpty()) {
                return List.<AsyncTaskRecord>of();
            }
            mapper.markClaimedTasksByIds(ids, claimToken, claimUntil);
            List<AsyncTaskRecord> records = mapper.selectTasksByIds(ids);
            for (AsyncTaskRecord record : records) {
                record.setClaimToken(claimToken);
                record.setClaimUntil(claimUntil);
                record.setStatus("RUNNING");
            }
            return records;
        });

        if (claimed == null || claimed.isEmpty()) {
            return List.of();
        }

        return claimed.stream()
                .sorted(Comparator.comparing(AsyncTaskRecord::getPriority, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AsyncTaskRecord::getId))
                .toList();
    }

    @Override
    public AsyncTaskRecord getTaskById(long taskId) {
        if (taskId <= 0) {
            return null;
        }
        return mapper.selectTaskById(taskId);
    }

    @Override
    public long startAttempt(AsyncTaskAttemptRecord record) {
        if (record == null) {
            throw new OpenLineageWriteException("任务明细入库参数缺失");
        }
        mapper.insertTaskAttempt(record);
        if (record.getId() == null || record.getId() <= 0) {
            throw new OpenLineageWriteException("任务明细入库失败");
        }
        return record.getId();
    }

    @Override
    public void finishAttempt(long attemptId,
                              String status,
                              LocalDateTime finishedAt,
                              Long latencyMs,
                              String errorType,
                              String errorMessage) {
        if (attemptId <= 0) {
            return;
        }
        mapper.updateTaskAttempt(attemptId, status, finishedAt, latencyMs, errorType, errorMessage);
    }

    @Override
    public void markTaskSucceeded(long taskId, String claimToken) {
        if (taskId <= 0 || claimToken == null || claimToken.isBlank()) {
            return;
        }
        mapper.markTaskSucceeded(taskId, claimToken);
    }

    @Override
    public void markTaskFailed(long taskId,
                               String claimToken,
                               String lastError,
                               LocalDateTime nextRunAt,
                               boolean dead) {
        if (taskId <= 0 || claimToken == null || claimToken.isBlank()) {
            return;
        }
        mapper.markTaskFailed(taskId, claimToken, lastError, nextRunAt, dead);
    }

    @Override
    public String createBatch(AsyncBatchRecord record) {
        if (record == null) {
            throw new OpenLineageWriteException("批次记录参数缺失");
        }
        if (record.getBatchNo() == null || record.getBatchNo().isBlank()) {
            record.setBatchNo("BATCH-" + UUID.randomUUID());
        }
        mapper.insertBatch(record);
        return record.getBatchNo();
    }

    @Override
    public void finishBatch(String batchNo,
                            int successCount,
                            int failedCount,
                            String status,
                            LocalDateTime finishedAt) {
        if (batchNo == null || batchNo.isBlank()) {
            return;
        }
        mapper.updateBatch(batchNo, successCount, failedCount, status, finishedAt);
    }

    @Override
    public LocalDateTime computeNextRunAt(int retryCount, LocalDateTime now) {
        LocalDateTime baseline = now == null ? LocalDateTime.now(ZoneOffset.UTC) : now;
        int[] minuteBackoff = {1, 5, 15, 60, 360};
        int idx = Math.min(Math.max(retryCount - 1, 0), minuteBackoff.length - 1);
        return baseline.plusMinutes(minuteBackoff[idx]);
    }

    @Override
    public String classifyErrorType(Throwable throwable) {
        if (throwable == null) {
            return "UNKNOWN";
        }
        if (throwable instanceof IllegalArgumentException) {
            return "VALIDATION_ERROR";
        }
        if (throwable instanceof IllegalStateException) {
            return "STATE_ERROR";
        }
        return throwable.getClass().getSimpleName();
    }

    @Override
    public String truncateError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getMessage();
        String normalized = (message == null || message.isBlank())
                ? throwable.getClass().getSimpleName()
                : throwable.getClass().getSimpleName() + ": " + message;
        if (normalized.length() <= 1000) {
            return normalized;
        }
        return normalized.substring(0, 1000);
    }

    @Override
    public Duration claimTimeout() {
        return claimTimeout;
    }

    private void insertLineageEvent(OpenLineageEventEnvelope envelope,
                                    TenantContext tenantContext,
                                    String runEventType) {
        if (envelope == null || tenantContext == null) {
            throw new OpenLineageWriteException("lineage 事件入库参数缺失");
        }

        LineageEventRecord record = LineageEventRecord.builder()
                .tenantId(tenantContext.tenantId())
                .tenantCode(requireText(tenantContext.tenantCode(), "tenantCode"))
                .tenantName(requireText(tenantContext.tenantName(), "tenantName"))
                .eventTime(LocalDateTime.ofInstant(envelope.eventInstant(), ZoneOffset.UTC))
                .eventType(runEventType)
                .runUuid(envelope.runId())
                .jobName(envelope.jobName())
                .jobNamespace(envelope.jobNamespace())
                .producer(envelope.producer())
                .internalEventType(envelope.getInternalEventType())
                .eventJson(envelope.serializeForStorage())
                .build();
        mapper.insertLineageEvent(record);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new OpenLineageWriteException("字段缺失: %s", field);
        }
        return value;
    }
}
