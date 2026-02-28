package com.sunny.datapillar.openlineage.async;

import com.sunny.datapillar.openlineage.model.AsyncTaskType;
import org.springframework.stereotype.Component;

/**
 * 异步任务派发器。
 */
@Component
public class AsyncTaskDispatcher {

    private final EmbeddingTaskWorker embeddingTaskWorker;
    private final SqlSummaryWorker sqlSummaryWorker;

    public AsyncTaskDispatcher(EmbeddingTaskWorker embeddingTaskWorker,
                               SqlSummaryWorker sqlSummaryWorker) {
        this.embeddingTaskWorker = embeddingTaskWorker;
        this.sqlSummaryWorker = sqlSummaryWorker;
    }

    public void dispatch(AsyncTaskType taskType, long taskId, String claimToken) {
        if (taskType == null || claimToken == null || claimToken.isBlank()) {
            return;
        }
        if (taskType == AsyncTaskType.EMBEDDING) {
            embeddingTaskWorker.submit(taskId, claimToken);
            return;
        }
        if (taskType == AsyncTaskType.SQL_SUMMARY) {
            sqlSummaryWorker.submit(taskId, claimToken);
        }
    }
}
