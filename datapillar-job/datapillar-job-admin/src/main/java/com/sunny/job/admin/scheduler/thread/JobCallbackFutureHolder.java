package com.sunny.job.admin.scheduler.thread;

import com.sunny.job.admin.model.DatapillarJobLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理任务执行的 Future，用于同步等待回调完成
 *
 * @author datapillar-job-admin
 * @date 2025-01-13
 */
public class JobCallbackFutureHolder {
    private static final Logger logger = LoggerFactory.getLogger(JobCallbackFutureHolder.class);

    // jobId -> Future<DatapillarJobLog>
    private static final ConcurrentHashMap<Integer, CompletableFuture<DatapillarJobLog>> futureMap = new ConcurrentHashMap<>();

    /**
     * 注册 Future（trigger 时调用）
     */
    public static CompletableFuture<DatapillarJobLog> registerFuture(int jobId) {
        CompletableFuture<DatapillarJobLog> future = new CompletableFuture<>();
        futureMap.put(jobId, future);
        logger.debug("注册Future: jobId={}", jobId);
        return future;
    }

    /**
     * 完成 Future（callback 回调时调用）
     */
    public static void completeFuture(int jobId, DatapillarJobLog log) {
        CompletableFuture<DatapillarJobLog> future = futureMap.remove(jobId);
        if (future != null) {
            future.complete(log);
            logger.debug("完成Future: jobId={}, handleCode={}", jobId, log.getHandleCode());
        }
    }

    /**
     * 取消等待（发生异常时清理）
     */
    public static void cancelFuture(int jobId) {
        CompletableFuture<DatapillarJobLog> future = futureMap.remove(jobId);
        if (future != null) {
            future.cancel(true);
            logger.debug("取消Future: jobId={}", jobId);
        }
    }
}
