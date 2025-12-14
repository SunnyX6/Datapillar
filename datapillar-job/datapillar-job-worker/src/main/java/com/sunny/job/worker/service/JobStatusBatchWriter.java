package com.sunny.job.worker.service;

import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.worker.domain.mapper.JobRunMapper;
import com.sunny.job.worker.domain.mapper.JobRunMapper.StatusUpdate;
import com.sunny.job.worker.pekko.ddata.JobRunStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务状态批量写入服务
 * <p>
 * 核心优化：
 * - 收集状态更新到队列，定时批量写入 DB
 * - 减少 DB 写入频率，提升吞吐量
 * - 异步写入，不阻塞任务执行流程
 * <p>
 * 设计要点：
 * - 写入频率：默认每 100ms 批量写入一次
 * - 批量大小：每次最多写入 500 条
 * - 线程安全：使用 ConcurrentLinkedQueue
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Service
public class JobStatusBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(JobStatusBatchWriter.class);

    /**
     * 待写入的状态更新队列
     */
    private final Queue<StatusUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();

    /**
     * 队列当前大小（用于监控）
     */
    private final AtomicInteger queueSize = new AtomicInteger(0);

    private final JobRunMapper jobRunMapper;
    private final JobRunStateManager jobRunStateManager;

    /**
     * 每次批量写入的最大数量
     */
    @Value("${datapillar.job.worker.batch-write-size:2000}")
    private int batchWriteSize;

    /**
     * 队列满时强制刷新的阈值
     */
    @Value("${datapillar.job.worker.batch-write-threshold:5000}")
    private int forceFlushThreshold;

    /**
     * 最大重试次数（超过后丢弃更新并记录告警）
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 重试计数器（jobRunId -> 重试次数）
     */
    private final Map<Long, Integer> retryCountMap = new ConcurrentHashMap<>();

    public JobStatusBatchWriter(JobRunMapper jobRunMapper, JobRunStateManager jobRunStateManager) {
        this.jobRunMapper = jobRunMapper;
        this.jobRunStateManager = jobRunStateManager;
    }

    /**
     * 提交状态更新（异步）
     * <p>
     * 将状态更新放入队列，等待批量写入
     *
     * @param jobRunId  任务执行实例 ID
     * @param status    新状态
     * @param workerId  执行者
     * @param startTime 开始时间（毫秒）
     * @param endTime   结束时间（毫秒）
     * @param message   结果消息
     */
    public void submit(long jobRunId, int status, String workerId, Long startTime, Long endTime, String message) {
        StatusUpdate update = new StatusUpdate(jobRunId, status, workerId, startTime, endTime, message);
        pendingUpdates.offer(update);
        int size = queueSize.incrementAndGet();

        // 队列满时强制刷新
        if (size >= forceFlushThreshold) {
            log.warn("队列达到阈值 {}，强制刷新", forceFlushThreshold);
            flushBatch();
        }
    }

    /**
     * 定时批量写入
     * <p>
     * 每 50ms 执行一次，将队列中的更新批量写入 DB 并同步到 CRDT
     */
    @Scheduled(fixedRate = 50)
    public void flushBatch() {
        if (pendingUpdates.isEmpty()) {
            return;
        }

        List<StatusUpdate> batch = new ArrayList<>(batchWriteSize);
        StatusUpdate update;

        // 从队列取出最多 batchWriteSize 条
        while (batch.size() < batchWriteSize && (update = pendingUpdates.poll()) != null) {
            batch.add(update);
            queueSize.decrementAndGet();
        }

        if (batch.isEmpty()) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            int affected = jobRunMapper.batchUpdateStatus(batch);
            long elapsed = System.currentTimeMillis() - startTime;

            // 同步更新 CRDT（DB 写入成功后）
            syncToCrdt(batch);

            // 清理成功写入的重试计数
            for (StatusUpdate item : batch) {
                retryCountMap.remove(item.jobRunId());
            }

            if (elapsed > 100) {
                log.warn("批量写入耗时较长: count={}, affected={}, elapsed={}ms", batch.size(), affected, elapsed);
            } else {
                log.debug("批量写入完成: count={}, affected={}, elapsed={}ms", batch.size(), affected, elapsed);
            }
        } catch (Exception e) {
            log.error("批量写入失败: count={}", batch.size(), e);
            // 失败的更新检查重试次数后决定是否重新放回队列
            for (StatusUpdate failed : batch) {
                int retryCount = retryCountMap.getOrDefault(failed.jobRunId(), 0) + 1;
                if (retryCount <= MAX_RETRY_COUNT) {
                    retryCountMap.put(failed.jobRunId(), retryCount);
                    pendingUpdates.offer(failed);
                    queueSize.incrementAndGet();
                    log.warn("状态更新重试: jobRunId={}, retry={}/{}",
                            failed.jobRunId(), retryCount, MAX_RETRY_COUNT);
                } else {
                    // 超过重试次数，丢弃并记录告警
                    retryCountMap.remove(failed.jobRunId());
                    log.error("状态更新超过最大重试次数，已丢弃: jobRunId={}, status={}",
                            failed.jobRunId(), failed.status());
                }
            }
        }
    }

    /**
     * 同步状态更新到 CRDT
     * <p>
     * DB 写入成功后，批量更新 CRDT 缓存，保证读写分离的一致性
     */
    private void syncToCrdt(List<StatusUpdate> batch) {
        if (jobRunStateManager == null) {
            return;
        }

        Map<Long, JobStatus> statusMap = new HashMap<>(batch.size());
        for (StatusUpdate update : batch) {
            JobStatus status = JobStatus.of(update.status());
            if (status != null) {
                statusMap.put(update.jobRunId(), status);
            }
        }

        if (!statusMap.isEmpty()) {
            jobRunStateManager.batchUpdateStatus(statusMap);
            log.debug("同步 {} 个状态更新到 CRDT", statusMap.size());
        }
    }

    /**
     * 获取队列大小（监控用）
     */
    public int getQueueSize() {
        return queueSize.get();
    }

    /**
     * 强制刷新所有待写入的更新
     * <p>
     * 用于应用关闭时确保所有更新都写入 DB
     */
    public void forceFlushAll() {
        log.info("强制刷新所有待写入更新，当前队列大小: {}", queueSize.get());
        while (!pendingUpdates.isEmpty()) {
            flushBatch();
        }
        log.info("强制刷新完成");
    }
}
