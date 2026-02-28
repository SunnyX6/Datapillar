package com.sunny.datapillar.openlineage.async;

import com.sunny.datapillar.openlineage.dao.OpenLineageEventDao;
import com.sunny.datapillar.openlineage.dao.OpenLineageGraphDao;
import com.sunny.datapillar.openlineage.model.AsyncTaskRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskType;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 向量化任务 Worker。
 */
@Component
public class EmbeddingTaskWorker extends AbstractAsyncTaskWorker {

    public EmbeddingTaskWorker(OpenLineageEventDao openLineageEventDao,
                               OpenLineageGraphDao openLineageGraphDao,
                               @Qualifier("openLineageWorkerExecutor") Executor workerExecutor,
                               @Value("${openlineage.worker.embedding.batch-size:32}") int batchSize,
                               @Value("${openlineage.worker.embedding.recovery-limit:64}") int recoveryLimit,
                               @Value("${openlineage.worker.recovery-interval-seconds:30}") int recoveryIntervalSeconds) {
        super(
                openLineageEventDao,
                openLineageGraphDao,
                workerExecutor,
                AsyncTaskType.EMBEDDING,
                batchSize,
                recoveryLimit,
                recoveryIntervalSeconds);
    }

    @Override
    protected void executeTask(AsyncTaskRecord task) {
        if (task.getTenantId() == null || task.getTenantId() <= 0) {
            throw new IllegalArgumentException("向量任务缺少租户信息");
        }

        String content = openLineageGraphDao.fetchResourceContent(task.getTenantId(), task.getResourceType(), task.getResourceId());
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("向量任务缺少可用内容");
        }

        String provider = WorkerPayloadSupport.extractProvider(task.getModelFingerprint());
        double[] embedding = WorkerPayloadSupport.buildEmbeddingVector(content);
        openLineageGraphDao.writeEmbedding(task.getTenantId(), task.getResourceId(), provider, embedding);
    }

    @Override
    protected String workerTag() {
        return "EmbeddingTaskWorker";
    }
}
