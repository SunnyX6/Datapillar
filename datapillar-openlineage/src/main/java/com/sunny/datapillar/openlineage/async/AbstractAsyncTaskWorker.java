package com.sunny.datapillar.openlineage.async;

import com.sunny.datapillar.openlineage.dao.OpenLineageEventDao;
import com.sunny.datapillar.openlineage.dao.OpenLineageGraphDao;
import com.sunny.datapillar.openlineage.model.AsyncBatchRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskAttemptRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskRecord;
import com.sunny.datapillar.openlineage.model.AsyncTaskType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 异步任务 Worker 基类。
 */
@Slf4j
public abstract class AbstractAsyncTaskWorker {

    private final OpenLineageEventDao openLineageEventDao;
    protected final OpenLineageGraphDao openLineageGraphDao;
    private final Executor workerExecutor;
    private final AsyncTaskType taskType;
    private final int batchSize;
    private final int recoveryLimit;
    private final int recoveryIntervalSeconds;

    private final BlockingQueue<AsyncTaskMessage> pushQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService recoveryScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final String workerId;

    protected AbstractAsyncTaskWorker(OpenLineageEventDao openLineageEventDao,
                                      OpenLineageGraphDao openLineageGraphDao,
                                      Executor workerExecutor,
                                      AsyncTaskType taskType,
                                      int batchSize,
                                      int recoveryLimit,
                                      int recoveryIntervalSeconds) {
        this.openLineageEventDao = openLineageEventDao;
        this.openLineageGraphDao = openLineageGraphDao;
        this.workerExecutor = workerExecutor;
        this.taskType = taskType;
        this.batchSize = Math.max(1, batchSize);
        this.recoveryLimit = Math.max(1, recoveryLimit);
        this.recoveryIntervalSeconds = Math.max(5, recoveryIntervalSeconds);
        this.workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + taskType.name().toLowerCase();
    }

    @PostConstruct
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(this::consumeLoop, workerExecutor);
        recoveryScheduler.scheduleWithFixedDelay(this::recoverSafely,
                recoveryIntervalSeconds,
                recoveryIntervalSeconds,
                TimeUnit.SECONDS);
        log.info("{} started: batchSize={}, recoveryLimit={}, recoveryIntervalSeconds={}",
                workerTag(),
                batchSize,
                recoveryLimit,
                recoveryIntervalSeconds);
    }

    @PreDestroy
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        recoveryScheduler.shutdownNow();
        log.info("{} stopped", workerTag());
    }

    public void submit(long taskId, String claimToken) {
        if (taskId <= 0 || claimToken == null || claimToken.isBlank()) {
            return;
        }
        pushQueue.offer(new AsyncTaskMessage(taskId, claimToken));
    }

    private void consumeLoop() {
        while (running.get()) {
            try {
                AsyncTaskMessage first = pushQueue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                List<AsyncTaskMessage> messages = new ArrayList<>();
                messages.add(first);
                pushQueue.drainTo(messages, batchSize - 1);
                processMessages(messages);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.error("{} consume loop failed", workerTag(), ex);
            }
        }
    }

    private void processMessages(List<AsyncTaskMessage> messages) {
        List<AsyncTaskRecord> claimedTasks = messages.stream()
                .map(message -> openLineageEventDao.getTaskById(message.taskId()))
                .filter(this::isExecutableTask)
                .filter(task -> taskType.name().equalsIgnoreCase(task.getTaskType()))
                .toList();

        if (claimedTasks.isEmpty()) {
            return;
        }

        Map<String, List<AsyncTaskRecord>> grouped = claimedTasks.stream().collect(Collectors.groupingBy(
                this::groupKey,
                LinkedHashMap::new,
                Collectors.toList()));

        for (List<AsyncTaskRecord> batchTasks : grouped.values()) {
            executeBatch(batchTasks);
        }
    }

    private void executeBatch(List<AsyncTaskRecord> batchTasks) {
        if (batchTasks == null || batchTasks.isEmpty()) {
            return;
        }

        AsyncTaskRecord first = batchTasks.getFirst();
        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
        String batchNo = openLineageEventDao.createBatch(AsyncBatchRecord.builder()
                .taskType(taskType.name())
                .tenantId(first.getTenantId())
                .modelFingerprint(first.getModelFingerprint())
                .workerId(workerId)
                .plannedSize(batchTasks.size())
                .successCount(0)
                .failedCount(0)
                .startedAt(startedAt)
                .status("RUNNING")
                .build());

        int successCount = 0;
        int failedCount = 0;

        for (AsyncTaskRecord task : batchTasks) {
            boolean success = executeSingleTask(task, batchNo);
            if (success) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        String status = failedCount == 0 ? "SUCCEEDED" : "FAILED";
        openLineageEventDao.finishBatch(
                batchNo,
                successCount,
                failedCount,
                status,
                LocalDateTime.now(ZoneOffset.UTC));
    }

    private boolean executeSingleTask(AsyncTaskRecord task, String batchNo) {
        if (!isExecutableTask(task)) {
            return false;
        }

        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
        long startedMillis = System.currentTimeMillis();
        long attemptId = openLineageEventDao.startAttempt(AsyncTaskAttemptRecord.builder()
                .taskId(task.getId())
                .attemptNo(Math.max(1, defaultIfNull(task.getRetryCount(), 0) + 1))
                .workerId(workerId)
                .startedAt(startedAt)
                .status("RUNNING")
                .batchNo(batchNo)
                .inputSize(1)
                .build());

        try {
            executeTask(task);
            long latency = System.currentTimeMillis() - startedMillis;
            openLineageEventDao.finishAttempt(
                    attemptId,
                    "SUCCEEDED",
                    LocalDateTime.now(ZoneOffset.UTC),
                    latency,
                    null,
                    null);
            openLineageEventDao.markTaskSucceeded(task.getId(), task.getClaimToken());
            return true;
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - startedMillis;
            int retryCount = defaultIfNull(task.getRetryCount(), 0) + 1;
            int maxRetry = Math.max(1, defaultIfNull(task.getMaxRetry(), 5));
            boolean dead = retryCount >= maxRetry;
            LocalDateTime nextRunAt = openLineageEventDao.computeNextRunAt(retryCount, LocalDateTime.now(ZoneOffset.UTC));

            String errorType = openLineageEventDao.classifyErrorType(ex);
            String errorMessage = openLineageEventDao.truncateError(ex);

            openLineageEventDao.finishAttempt(
                    attemptId,
                    "FAILED",
                    LocalDateTime.now(ZoneOffset.UTC),
                    latency,
                    errorType,
                    errorMessage);
            openLineageEventDao.markTaskFailed(task.getId(), task.getClaimToken(), errorMessage, nextRunAt, dead);
            log.warn("{} task execution failed: taskId={}, dead={}, reason={}", workerTag(), task.getId(), dead, errorMessage);
            return false;
        }
    }

    private void recoverSafely() {
        if (!running.get()) {
            return;
        }
        try {
            recover();
        } catch (Exception ex) {
            log.error("{} recover loop failed", workerTag(), ex);
        }
    }

    private void recover() {
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimUntil = LocalDateTime.now(ZoneOffset.UTC).plus(openLineageEventDao.claimTimeout());
        List<AsyncTaskRecord> recoverable =
                openLineageEventDao.claimRecoverableTasks(taskType, recoveryLimit, claimToken, claimUntil);
        for (AsyncTaskRecord task : recoverable) {
            String token = task.getClaimToken() == null ? claimToken : task.getClaimToken();
            submit(task.getId(), token);
        }
    }

    private String groupKey(AsyncTaskRecord task) {
        return task.getTenantId() + "|" + task.getModelFingerprint();
    }

    private boolean isExecutableTask(AsyncTaskRecord task) {
        if (task == null || task.getId() == null || task.getId() <= 0) {
            return false;
        }
        if (task.getClaimToken() == null || task.getClaimToken().isBlank()) {
            return false;
        }
        return "RUNNING".equalsIgnoreCase(task.getStatus());
    }

    protected String workerId() {
        return workerId;
    }

    protected OpenLineageEventDao eventDao() {
        return openLineageEventDao;
    }

    protected abstract void executeTask(AsyncTaskRecord task);

    protected abstract String workerTag();

    private int defaultIfNull(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
