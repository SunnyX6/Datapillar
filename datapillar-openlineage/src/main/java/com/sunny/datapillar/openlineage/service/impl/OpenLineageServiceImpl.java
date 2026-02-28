package com.sunny.datapillar.openlineage.service.impl;

import com.sunny.datapillar.openlineage.async.AsyncTaskDispatcher;
import com.sunny.datapillar.openlineage.dao.OpenLineageDao;
import com.sunny.datapillar.openlineage.dao.OpenLineageEventDao;
import com.sunny.datapillar.openlineage.exception.OpenLineageWriteException;
import com.sunny.datapillar.openlineage.model.AsyncTaskCandidate;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.OpenLineageUpdateResult;
import com.sunny.datapillar.openlineage.security.TenantContext;
import com.sunny.datapillar.openlineage.service.OpenLineageService;
import io.openlineage.client.OpenLineage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * OpenLineage 服务实现。
 */
@Service
public class OpenLineageServiceImpl implements OpenLineageService {

    private final OpenLineageDao openLineageDao;
    private final OpenLineageEventDao openLineageEventDao;
    private final AsyncTaskDispatcher asyncTaskDispatcher;
    private final Executor openLineageExecutor;

    public OpenLineageServiceImpl(OpenLineageDao openLineageDao,
                                  OpenLineageEventDao openLineageEventDao,
                                  AsyncTaskDispatcher asyncTaskDispatcher,
                                  @Qualifier("openLineageExecutor") Executor openLineageExecutor) {
        this.openLineageDao = openLineageDao;
        this.openLineageEventDao = openLineageEventDao;
        this.asyncTaskDispatcher = asyncTaskDispatcher;
        this.openLineageExecutor = openLineageExecutor;
    }

    @Override
    public CompletableFuture<Void> createAsync(OpenLineage.RunEvent event,
                                               OpenLineageEventEnvelope envelope,
                                               TenantContext tenantContext) {
        CompletableFuture<Void> openlineage = CompletableFuture.runAsync(
                () -> openLineageDao.createLineageEvent(event, envelope, tenantContext),
                openLineageExecutor);

        CompletableFuture<OpenLineageUpdateResult> datapillar = CompletableFuture.supplyAsync(
                () -> openLineageDao.updateDatapillarModel(event, envelope, tenantContext),
                openLineageExecutor);

        return CompletableFuture.allOf(datapillar, openlineage)
                .thenCompose(ignored -> enqueueAndDispatch(datapillar.join(), tenantContext));
    }

    @Override
    public CompletableFuture<Void> createAsync(OpenLineage.DatasetEvent event,
                                               OpenLineageEventEnvelope envelope,
                                               TenantContext tenantContext) {
        CompletableFuture<Void> openlineage = CompletableFuture.runAsync(
                () -> openLineageDao.createDatasetEvent(event, envelope, tenantContext),
                openLineageExecutor);

        CompletableFuture<OpenLineageUpdateResult> datapillar = CompletableFuture.supplyAsync(
                () -> openLineageDao.updateDatapillarModel(event, envelope, tenantContext),
                openLineageExecutor);

        return CompletableFuture.allOf(datapillar, openlineage)
                .thenCompose(ignored -> enqueueAndDispatch(datapillar.join(), tenantContext));
    }

    @Override
    public CompletableFuture<Void> createAsync(OpenLineage.JobEvent event,
                                               OpenLineageEventEnvelope envelope,
                                               TenantContext tenantContext) {
        CompletableFuture<Void> openlineage = CompletableFuture.runAsync(
                () -> openLineageDao.createJobEvent(event, envelope, tenantContext),
                openLineageExecutor);

        CompletableFuture<OpenLineageUpdateResult> datapillar = CompletableFuture.supplyAsync(
                () -> openLineageDao.updateDatapillarModel(event, envelope, tenantContext),
                openLineageExecutor);

        return CompletableFuture.allOf(datapillar, openlineage)
                .thenCompose(ignored -> enqueueAndDispatch(datapillar.join(), tenantContext));
    }

    private CompletableFuture<Void> enqueueAndDispatch(OpenLineageUpdateResult updateResult,
                                                       TenantContext tenantContext) {
        List<AsyncTaskCandidate> candidates = updateResult == null ? List.of() : updateResult.taskCandidates();
        if (candidates.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            for (AsyncTaskCandidate candidate : candidates) {
                long taskId = openLineageEventDao.upsertAsyncTask(tenantContext, candidate);
                String claimToken = UUID.randomUUID().toString();
                LocalDateTime claimUntil = LocalDateTime.now(ZoneOffset.UTC).plus(openLineageEventDao.claimTimeout());
                boolean claimed = openLineageEventDao.claimTaskForPush(taskId, claimToken, claimUntil);
                if (claimed) {
                    asyncTaskDispatcher.dispatch(candidate.taskType(), taskId, claimToken);
                }
            }
        }, openLineageExecutor).exceptionally(throwable -> {
            throw new OpenLineageWriteException(throwable, "enqueue/push 异步任务失败");
        });
    }
}
