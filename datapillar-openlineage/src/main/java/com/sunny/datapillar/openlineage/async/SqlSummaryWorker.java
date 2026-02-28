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
 * SQL 摘要任务 Worker。
 */
@Component
public class SqlSummaryWorker extends AbstractAsyncTaskWorker {

    public SqlSummaryWorker(OpenLineageEventDao openLineageEventDao,
                            OpenLineageGraphDao openLineageGraphDao,
                            @Qualifier("openLineageWorkerExecutor") Executor workerExecutor,
                            @Value("${openlineage.worker.sql-summary.batch-size:16}") int batchSize,
                            @Value("${openlineage.worker.sql-summary.recovery-limit:32}") int recoveryLimit,
                            @Value("${openlineage.worker.recovery-interval-seconds:30}") int recoveryIntervalSeconds) {
        super(
                openLineageEventDao,
                openLineageGraphDao,
                workerExecutor,
                AsyncTaskType.SQL_SUMMARY,
                batchSize,
                recoveryLimit,
                recoveryIntervalSeconds);
    }

    @Override
    protected void executeTask(AsyncTaskRecord task) {
        if (task.getTenantId() == null || task.getTenantId() <= 0) {
            throw new IllegalArgumentException("SQL 摘要任务缺少租户信息");
        }

        String sql = openLineageGraphDao.fetchResourceContent(task.getTenantId(), task.getResourceType(), task.getResourceId());
        if (sql == null || sql.isBlank()) {
            throw new IllegalStateException("SQL 摘要任务缺少 SQL 内容");
        }

        String summary = WorkerPayloadSupport.buildSqlSummary(sql);
        String tags = WorkerPayloadSupport.buildSqlTags(sql);
        String provider = WorkerPayloadSupport.extractProvider(task.getModelFingerprint());
        double[] embedding = WorkerPayloadSupport.buildEmbeddingVector(summary.isBlank() ? sql : summary);

        openLineageGraphDao.writeSqlSummary(task.getTenantId(), task.getResourceId(), summary, tags, provider, embedding);
    }

    @Override
    protected String workerTag() {
        return "SqlSummaryWorker";
    }
}
