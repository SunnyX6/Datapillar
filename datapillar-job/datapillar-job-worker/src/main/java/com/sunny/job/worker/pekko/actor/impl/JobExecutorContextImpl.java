package com.sunny.job.worker.pekko.actor.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunny.job.core.cron.CronUtils;
import com.sunny.job.core.enums.AlarmTriggerEvent;
import com.sunny.job.core.enums.BlockStrategy;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.RouteStrategy;
import com.sunny.job.core.enums.TriggerType;
import com.sunny.job.core.enums.WorkflowStatus;
import com.sunny.job.core.handler.JobContext;
import com.sunny.job.core.handler.JobHandlerExecutor;
import com.sunny.job.core.id.IdGenerator;
import com.sunny.job.core.message.ExecutorMessage.ExecuteJob;
import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.worker.alert.AlertResult;
import com.sunny.job.worker.alert.AlertSenderService;
import com.sunny.job.worker.domain.entity.*;
import com.sunny.job.worker.domain.mapper.*;
import com.sunny.job.worker.pekko.actor.JobExecutorContext;
import com.sunny.job.worker.pekko.ddata.JobRunLocalCache;
import com.sunny.job.worker.pekko.ddata.MaxJobRunIdState;
import com.sunny.job.worker.service.JobComponentCacheService;
import com.sunny.job.worker.service.JobLogService;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JobExecutor 上下文实现
 * <p>
 * 对接 DB 更新状态 + 执行任务
 * <p>
 * 线程模型：
 * - 任务执行由 Pekko Virtual Thread Dispatcher 管理
 * - 使用 Semaphore 控制最大并发任务数
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component
public class JobExecutorContextImpl implements JobExecutorContext {

    private static final Logger log = LoggerFactory.getLogger(JobExecutorContextImpl.class);

    private final JobRunMapper jobRunMapper;
    private final JobWorkflowRunMapper workflowRunMapper;
    private final JobWorkflowMapper workflowMapper;
    private final JobInfoMapper jobInfoMapper;
    private final JobDependencyMapper dependencyMapper;
    private final JobWorkflowDependencyMapper workflowDependencyMapper;
    private final JobAlertMapper alertMapper;
    private final AlertSenderService alertSenderService;
    private final JobHandlerExecutor handlerExecutor;
    private final JobLogService jobLogService;
    private final JobRunLocalCache jobRunLocalCache;
    private final MaxJobRunIdState maxJobRunIdManager;
    private final IdGenerator idGenerator;
    private final JobComponentCacheService componentCacheService;

    /**
     * 最大并发任务数
     */
    @Value("${datapillar.job.worker.max-concurrent-tasks:200}")
    private int maxConcurrentTasks;

    /**
     * 并发控制信号量获取超时时间（秒）
     */
    @Value("${datapillar.job.worker.semaphore-timeout-seconds:30}")
    private int semaphoreTimeoutSeconds;

    /**
     * Bucket 总数
     */
    @Value("${datapillar.job.worker.bucket-count:1024}")
    private int bucketCount;

    /**
     * 并发控制信号量
     */
    private Semaphore concurrencyLimiter;

    /**
     * 当前执行中的任务数（用于监控）
     */
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);

    /**
     * 异步执行器（用于告警发送等异步操作）
     */
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 告警规则缓存（按 jobId 缓存，5 分钟过期）
     * <p>
     * 优化：避免在任务完成时同步查询 DB
     */
    private final Cache<Long, List<JobAlertRule>> alertRuleCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final ActorSystem<Void> actorSystem;
    private String workerAddress;

    public JobExecutorContextImpl(JobRunMapper jobRunMapper,
                                  JobWorkflowRunMapper workflowRunMapper,
                                  JobWorkflowMapper workflowMapper,
                                  JobInfoMapper jobInfoMapper,
                                  JobDependencyMapper dependencyMapper,
                                  JobWorkflowDependencyMapper workflowDependencyMapper,
                                  JobAlertMapper alertMapper,
                                  AlertSenderService alertSenderService,
                                  JobHandlerExecutor handlerExecutor,
                                  JobLogService jobLogService,
                                  JobRunLocalCache jobRunLocalCache,
                                  MaxJobRunIdState maxJobRunIdManager,
                                  ActorSystem<Void> actorSystem,
                                  IdGenerator idGenerator,
                                  JobComponentCacheService componentCacheService) {
        this.jobRunMapper = jobRunMapper;
        this.workflowRunMapper = workflowRunMapper;
        this.workflowMapper = workflowMapper;
        this.jobInfoMapper = jobInfoMapper;
        this.dependencyMapper = dependencyMapper;
        this.workflowDependencyMapper = workflowDependencyMapper;
        this.alertMapper = alertMapper;
        this.alertSenderService = alertSenderService;
        this.handlerExecutor = handlerExecutor;
        this.jobLogService = jobLogService;
        this.jobRunLocalCache = jobRunLocalCache;
        this.maxJobRunIdManager = maxJobRunIdManager;
        this.actorSystem = actorSystem;
        this.idGenerator = idGenerator;
        this.componentCacheService = componentCacheService;
    }

    /**
     * 初始化并发控制信号量
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        this.concurrencyLimiter = new Semaphore(maxConcurrentTasks);
        // 使用 Pekko Cluster 地址
        this.workerAddress = Cluster.get(actorSystem).selfMember().address().toString();
        log.info("JobExecutorContext 初始化完成，最大并发任务数: {}, workerAddress: {}", maxConcurrentTasks, workerAddress);
    }

    @Override
    public ExecutionResult execute(ExecuteJob job) {
        log.debug("开始执行任务: jobRunId={}, jobName={}, splitRange=[{}, {})",
                job.jobRunId(), job.jobName(), job.splitStart(), job.splitEnd());

        // 尝试获取执行许可（带超时的阻塞等待，替代原来的硬拒绝）
        boolean acquired = false;
        try {
            acquired = concurrencyLimiter.tryAcquire(semaphoreTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取执行许可被中断: jobRunId={}", job.jobRunId());
            return ExecutionResult.failure("获取执行许可被中断");
        }

        if (!acquired) {
            log.warn("获取执行许可超时({}秒), 任务将重试: jobRunId={}, 当前运行={}/{}",
                    semaphoreTimeoutSeconds, job.jobRunId(), runningTaskCount.get(), maxConcurrentTasks);
            return ExecutionResult.failure("系统繁忙，获取执行许可超时，请稍后重试");
        }

        runningTaskCount.incrementAndGet();

        try {
            // 构建执行上下文
            JobContext context = buildJobContext(job);

            // 同步执行任务（在 Pekko Virtual Thread Dispatcher 上）
            JobHandlerExecutor.ExecutionResult result = handlerExecutor.execute(context, job.timeoutSeconds());

            // 返回执行结果
            if (result.timeout()) {
                return ExecutionResult.timeout(result.message());
            } else if (result.success()) {
                return ExecutionResult.success(result.message());
            } else {
                return ExecutionResult.failure(result.message());
            }

        } catch (Exception e) {
            log.error("任务执行异常: jobRunId={}", job.jobRunId(), e);
            return ExecutionResult.failure(e.getMessage());
        } finally {
            runningTaskCount.decrementAndGet();
            concurrencyLimiter.release();
        }
    }

    @Override
    public void cancelExecution(long jobRunId) {
        log.info("取消任务执行: jobRunId={}", jobRunId);
        // 任务取消通过 Actor 消息（CancelJob）+ Thread.interrupt() 实现
        // 由 Pekko Actor 生命周期管理
    }

    /**
     * 获取当前运行中的任务数
     *
     * @return 运行中的任务数
     */
    @Override
    public int getRunningTaskCount() {
        return runningTaskCount.get();
    }

    /**
     * 获取最大并发任务数
     *
     * @return 最大并发任务数
     */
    @Override
    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    /**
     * 获取可用执行许可数
     *
     * @return 可用许可数
     */
    @Override
    public int getAvailableCapacity() {
        return concurrencyLimiter.availablePermits();
    }

    /**
     * 检查是否有可用容量
     *
     * @return true 如果有可用容量
     */
    @Override
    public boolean hasCapacity() {
        return runningTaskCount.get() < maxConcurrentTasks;
    }

    @Override
    public void updateJobRunStatus(long jobRunId, JobStatus status, long splitStart) {
        log.debug("更新任务状态: jobRunId={}, status={}, splitStart={}", jobRunId, status, splitStart);

        Long startTime = null;
        Long endTime = null;

        if (status == JobStatus.RUNNING) {
            startTime = System.currentTimeMillis();
        } else if (status.isTerminal()) {
            endTime = System.currentTimeMillis();
        }

        // 1. 写入 DB（持久化）
        jobRunMapper.updateStatus(jobRunId, status.getCode(), null, workerAddress, startTime, endTime, null);

        // 2. 同步到 CRDT（广播）
        jobRunLocalCache.updateStatus(jobRunId, status);
    }

    @Override
    public void updateNextTriggerTimeIfNeeded(long workflowRunId) {
        log.debug("检查是否需要更新 nextTriggerTime: workflowRunId={}", workflowRunId);

        // 1. 使用 CAS 更新 workflow_run 状态为 RUNNING，同时获取 workflow 信息
        // 只有状态从 WAITING 变为 RUNNING 时才执行
        JobWorkflowRun workflowRun = workflowRunMapper.selectById(workflowRunId);
        if (workflowRun == null) {
            log.warn("workflow_run 不存在: workflowRunId={}", workflowRunId);
            return;
        }

        // 如果已经不是 WAITING 状态，说明已经被其他任务更新过了
        if (workflowRun.getStatus() != JobStatus.WAITING.getCode()) {
            log.debug("workflow_run 状态不是 WAITING，跳过 nextTriggerTime 更新: workflowRunId={}, status={}",
                    workflowRunId, workflowRun.getStatus());
            return;
        }

        // 2. 获取 workflow 定义，计算 nextTriggerTime
        JobWorkflow workflow = workflowMapper.selectById(workflowRun.getWorkflowId());
        if (workflow == null) {
            log.warn("workflow 不存在: workflowId={}", workflowRun.getWorkflowId());
            return;
        }

        // 3. 计算 nextTriggerTime
        TriggerType triggerType = TriggerType.of(workflow.getTriggerType());
        long nextTriggerTime = -1;
        if (triggerType != TriggerType.MANUAL && triggerType != TriggerType.API) {
            long now = System.currentTimeMillis();
            nextTriggerTime = CronUtils.calculateNextTriggerTime(triggerType, workflow.getTriggerValue(), now);
        }

        // 4. CAS 更新 workflow_run 状态和 nextTriggerTime
        int updated = workflowRunMapper.updateStatusAndNextTriggerTime(
                workflowRunId,
                JobStatus.WAITING.getCode(),
                JobStatus.RUNNING.getCode(),
                System.currentTimeMillis(),
                nextTriggerTime > 0 ? nextTriggerTime : null
        );

        if (updated > 0) {
            log.info("更新 workflow_run 状态为 RUNNING 并设置 nextTriggerTime: workflowRunId={}, nextTriggerTime={}",
                    workflowRunId, nextTriggerTime > 0 ? CronUtils.formatTimestamp(nextTriggerTime) : "null");
        } else {
            log.debug("workflow_run 状态已被其他任务更新: workflowRunId={}", workflowRunId);
        }
    }

    @Override
    public void updateJobRunForRetry(long jobRunId, int retryCount) {
        log.info("更新任务为重试状态: jobRunId={}, retryCount={}", jobRunId, retryCount);

        // 1. 写入 DB（系统自动重试，op 保持不变）
        jobRunMapper.updateForRetry(jobRunId, null, retryCount);

        // 2. 同步到 CRDT（重试状态回到 WAITING）
        jobRunLocalCache.updateStatus(jobRunId, JobStatus.WAITING);
    }

    @Override
    public void updateJobRunFinalStatus(long jobRunId, JobStatus status) {
        log.info("更新任务最终状态: jobRunId={}, status={}", jobRunId, status);

        // 1. 写入 DB
        jobRunMapper.updateStatus(jobRunId, status.getCode(), null, workerAddress, null, System.currentTimeMillis(), null);

        // 2. 同步到 CRDT
        jobRunLocalCache.updateStatus(jobRunId, status);
    }

    /**
     * 判断是否为分片任务
     */
    private boolean isShardingJob(ExecuteJob job) {
        return job.routeStrategy() == RouteStrategy.SHARDING && job.splitEnd() > job.splitStart();
    }

    /**
     * 构建任务执行上下文
     */
    private JobContext buildJobContext(ExecuteJob job) {
        return new JobContext(
                job.jobId(),
                job.jobRunId(),
                job.namespaceId(),
                job.jobName(),
                job.jobType(),
                job.jobParams(),
                job.retryCount(),
                job.splitStart(),
                job.splitEnd()
        );
    }

    // ==================== 生成下一个 job_run 相关 ====================

    @Override
    public boolean checkWorkflowRunCompleted(long workflowRunId) {
        List<Integer> statuses = jobRunMapper.selectStatusByWorkflowRunId(workflowRunId);
        if (statuses.isEmpty()) {
            return false;
        }

        for (Integer status : statuses) {
            JobStatus jobStatus = JobStatus.of(status);
            if (jobStatus == JobStatus.WAITING || jobStatus == JobStatus.RUNNING) {
                return false;
            }
        }
        return true;
    }

    @Override
    public JobStatus calculateWorkflowRunFinalStatus(long workflowRunId) {
        List<Integer> statuses = jobRunMapper.selectStatusByWorkflowRunId(workflowRunId);

        for (Integer status : statuses) {
            JobStatus jobStatus = JobStatus.of(status);
            if (!jobStatus.isSuccess()) {
                return JobStatus.FAIL;
            }
        }
        return JobStatus.SUCCESS;
    }

    @Override
    public void updateWorkflowRunStatus(long workflowRunId, JobStatus status, String message) {
        log.info("更新工作流状态: workflowRunId={}, status={}", workflowRunId, status);
        workflowRunMapper.updateStatus(workflowRunId, status.getCode(), null, System.currentTimeMillis(), message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateNextResult generateNextWorkflowRun(long workflowRunId) {
        log.info("检查是否需要生成下一个工作流执行实例: workflowRunId={}", workflowRunId);

        // 1. 获取当前 workflow_run
        JobWorkflowRun currentWorkflowRun = workflowRunMapper.selectById(workflowRunId);
        if (currentWorkflowRun == null) {
            log.warn("工作流执行实例不存在: workflowRunId={}", workflowRunId);
            return GenerateNextResult.empty();
        }

        Long workflowId = currentWorkflowRun.getWorkflowId();

        // 2. 查询工作流定义，检查是否仍然上线
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            log.warn("工作流定义不存在: workflowId={}", workflowId);
            return GenerateNextResult.empty();
        }

        // 2.1 检查工作流状态，已下线则不生成下一次
        WorkflowStatus workflowStatus = WorkflowStatus.of(workflow.getStatus());
        if (!workflowStatus.isOnline()) {
            log.info("工作流已下线，不生成下一次: workflowId={}, status={}", workflowId, workflowStatus);
            return GenerateNextResult.empty();
        }

        // 3. 检查触发类型，只有周期性触发才生成下一次
        TriggerType triggerType = TriggerType.of(workflow.getTriggerType());
        if (triggerType == TriggerType.MANUAL || triggerType == TriggerType.API) {
            log.info("手动/API触发类型，不自动生成下一次: workflowId={}", workflowId);
            return GenerateNextResult.empty();
        }

        // 4. 使用预计算的 nextTriggerTime（任务开始执行时已计算）
        Long nextTriggerTime = currentWorkflowRun.getNextTriggerTime();
        if (nextTriggerTime == null || nextTriggerTime <= 0) {
            // 兜底：如果没有预计算，重新计算
            log.warn("nextTriggerTime 未预计算，重新计算: workflowRunId={}", workflowRunId);
            long now = System.currentTimeMillis();
            nextTriggerTime = CronUtils.calculateNextTriggerTime(triggerType, workflow.getTriggerValue(), now);
        }

        if (nextTriggerTime <= 0) {
            log.warn("无法计算下次触发时间: workflowId={}, triggerType={}, triggerValue={}",
                    workflowId, triggerType, workflow.getTriggerValue());
            return GenerateNextResult.empty();
        }

        log.info("生成下一个工作流执行实例: workflowId={}, nextTriggerTime={}",
                workflowId, CronUtils.formatTimestamp(nextTriggerTime));

        // 5. 创建 workflow_run（使用 IdGenerator 生成 ID）
        long newWorkflowRunId = idGenerator.nextId();
        JobWorkflowRun workflowRun = new JobWorkflowRun();
        workflowRun.setId(newWorkflowRunId);
        workflowRun.setNamespaceId(workflow.getNamespaceId());
        workflowRun.setWorkflowId(workflowId);
        workflowRun.setTriggerType(workflow.getTriggerType());
        workflowRun.setTriggerTime(nextTriggerTime);
        workflowRun.setStatus(JobStatus.WAITING.getCode());
        workflowRunMapper.insert(workflowRun);

        // 6. 查询工作流下所有任务定义
        List<JobInfo> jobDefinitions = jobInfoMapper.selectByWorkflowId(workflowId);
        if (jobDefinitions.isEmpty()) {
            log.warn("工作流下没有任务定义: workflowId={}", workflowId);
            return GenerateNextResult.empty();
        }

        // 7. 查询依赖关系，确定哪些 job 有依赖（非入口 job）
        List<JobDependency> dependencies = dependencyMapper.selectByWorkflowId(workflowId);
        Set<Long> jobsWithDependency = new HashSet<>();
        for (JobDependency dep : dependencies) {
            jobsWithDependency.add(dep.getJobId());
        }

        // 8. 创建 job_run 记录（根据 job_info.triggerType 计算 triggerTime）
        List<JobRun> jobRuns = new ArrayList<>();
        Map<Long, JobRun> jobIdToRunMap = new HashMap<>();

        for (JobInfo job : jobDefinitions) {
            JobRun jobRun = new JobRun();
            jobRun.setId(idGenerator.nextId());
            jobRun.setNamespaceId(workflow.getNamespaceId());
            jobRun.setWorkflowRunId(newWorkflowRunId);
            jobRun.setWorkflowId(workflowId);
            jobRun.setJobId(job.getId());
            jobRun.setBucketId((int) (job.getId() % bucketCount));
            jobRun.setStatus(JobStatus.WAITING.getCode());
            jobRun.setPriority(job.getPriority());
            jobRun.setRetryCount(0);

            // 计算 job_run.triggerTime
            long jobTriggerTime = calculateJobTriggerTime(job, workflow, nextTriggerTime, jobsWithDependency.contains(job.getId()));
            jobRun.setTriggerTime(jobTriggerTime);

            // 设置 triggerType（使用 job 自己的，如果有；否则继承 workflow）
            if (job.getTriggerType() != null) {
                jobRun.setTriggerType(job.getTriggerType());
            } else {
                jobRun.setTriggerType(workflow.getTriggerType());
            }

            jobRuns.add(jobRun);
            jobIdToRunMap.put(job.getId(), jobRun);
        }

        jobRunMapper.batchInsert(jobRuns);

        // 9. 查询工作流内依赖关系并创建 job_run_dependency
        List<JobRunDependency> runDependencies = new ArrayList<>();

        // 9.1 处理工作流内依赖
        for (JobDependency dep : dependencies) {
            JobRun childRun = jobIdToRunMap.get(dep.getJobId());
            JobRun parentRun = jobIdToRunMap.get(dep.getParentJobId());

            if (childRun != null && parentRun != null) {
                JobRunDependency runDep = new JobRunDependency();
                runDep.setId(idGenerator.nextId());
                runDep.setWorkflowRunId(newWorkflowRunId);
                runDep.setJobRunId(childRun.getId());
                runDep.setParentRunId(parentRun.getId());
                runDependencies.add(runDep);
            }
        }

        // 9.2 处理跨工作流依赖
        List<JobWorkflowDependency> crossDependencies = workflowDependencyMapper.selectByWorkflowId(workflowId);
        for (JobWorkflowDependency crossDep : crossDependencies) {
            // 查询依赖工作流的最新成功 workflow_run
            Long dependWorkflowRunId = workflowDependencyMapper.selectLatestSuccessWorkflowRunId(crossDep.getDependWorkflowId());
            if (dependWorkflowRunId == null) {
                log.warn("跨工作流依赖的工作流没有成功的执行实例: dependWorkflowId={}", crossDep.getDependWorkflowId());
                continue;
            }

            // 查询依赖的 job_run_id
            Long dependJobRunId = workflowDependencyMapper.selectJobRunId(dependWorkflowRunId, crossDep.getDependJobId());
            if (dependJobRunId == null) {
                log.warn("跨工作流依赖的任务不存在: dependWorkflowRunId={}, dependJobId={}",
                        dependWorkflowRunId, crossDep.getDependJobId());
                continue;
            }

            // 找到当前工作流中没有上游依赖的任务（入口任务），将跨工作流依赖绑定到它们
            Set<Long> entryJobIds = findEntryJobIds(jobDefinitions, dependencies);
            for (Long entryJobId : entryJobIds) {
                JobRun entryJobRun = jobIdToRunMap.get(entryJobId);
                if (entryJobRun != null) {
                    JobRunDependency runDep = new JobRunDependency();
                    runDep.setId(idGenerator.nextId());
                    runDep.setWorkflowRunId(newWorkflowRunId);
                    runDep.setJobRunId(entryJobRun.getId());
                    runDep.setParentRunId(dependJobRunId);
                    runDependencies.add(runDep);
                    log.debug("添加跨工作流依赖: jobRunId={} -> dependJobRunId={}", entryJobRun.getId(), dependJobRunId);
                }
            }
        }

        // 9.3 批量插入依赖关系
        if (!runDependencies.isEmpty()) {
            dependencyMapper.batchInsertRunDependencies(runDependencies);
        }

        // 10. 构造 JobRunInfo 列表，用于本地注册到 Scheduler
        Map<Long, JobInfo> jobIdToInfoMap = jobDefinitions.stream()
                .collect(java.util.stream.Collectors.toMap(JobInfo::getId, j -> j));

        List<JobRunInfo> jobRunInfoList = new ArrayList<>();
        for (JobRun jr : jobRuns) {
            JobInfo jobInfo = jobIdToInfoMap.get(jr.getJobId());
            if (jobInfo == null) continue;

            JobRunInfo info = new JobRunInfo();
            info.setJobRunId(jr.getId());
            info.setWorkflowRunId(newWorkflowRunId);
            info.setWorkflowId(workflowId);
            info.setJobId(jr.getJobId());
            info.setBucketId(jr.getBucketId());
            info.setNamespaceId(jr.getNamespaceId());
            info.setJobName(jobInfo.getJobName());
            info.setJobType(componentCacheService.getComponentCode(jobInfo.getJobType()));
            info.setJobParams(jobInfo.getJobParams());
            info.setRouteStrategy(RouteStrategy.of(jobInfo.getRouteStrategy()));
            info.setBlockStrategy(BlockStrategy.of(jobInfo.getBlockStrategy()));
            info.setTimeoutSeconds(jobInfo.getTimeoutSeconds());
            info.setMaxRetryTimes(jobInfo.getMaxRetryTimes() != null ? jobInfo.getMaxRetryTimes() : 0);
            info.setRetryInterval(jobInfo.getRetryInterval() != null ? jobInfo.getRetryInterval() : 0);
            info.setPriority(jr.getPriority());
            info.setTriggerType(jr.getTriggerType());
            info.setTriggerTime(jr.getTriggerTime());
            info.setStatus(JobStatus.WAITING);
            info.setRetryCount(0);
            jobRunInfoList.add(info);
        }

        // 11. 通过 CRDT 广播 maxJobRunId，触发其他 Worker 增量加载
        if (!jobRunInfoList.isEmpty()) {
            long maxJobRunId = jobRunInfoList.stream().mapToLong(JobRunInfo::getJobRunId).max().orElse(0);
            if (maxJobRunId > 0) {
                maxJobRunIdManager.updateMaxId(maxJobRunId);
                log.info("广播 maxJobRunId: {}", maxJobRunId);
            }
        }

        log.info("成功生成下一个工作流执行实例: workflowRunId={}, jobRunCount={}", newWorkflowRunId, jobRunInfoList.size());

        return new GenerateNextResult(true, newWorkflowRunId, nextTriggerTime, jobRunInfoList);
    }

    /**
     * 计算 job_run 的 triggerTime
     * <p>
     * 规则：
     * - job_info.triggerType = NULL（继承）：
     *   - 入口 job（无依赖）：triggerTime = workflow.triggerTime
     *   - 非入口 job（有依赖）：triggerTime = 0（依赖满足即执行）
     * - job_info.triggerType 有值（独立触发）：
     *   - 根据 job 自己的 triggerType/triggerValue 计算
     *
     * @param job                 任务定义
     * @param workflow            工作流定义
     * @param workflowTriggerTime 工作流触发时间
     * @param hasDependency       是否有依赖
     * @return triggerTime（毫秒），0 表示依赖满足即执行
     */
    private long calculateJobTriggerTime(JobInfo job, JobWorkflow workflow, long workflowTriggerTime, boolean hasDependency) {
        // job 有独立触发配置
        if (job.getTriggerType() != null && job.getTriggerValue() != null) {
            TriggerType jobTriggerType = TriggerType.of(job.getTriggerType());
            return CronUtils.calculateNextTriggerTime(jobTriggerType, job.getTriggerValue(), workflowTriggerTime);
        }

        // 继承工作流触发配置
        if (hasDependency) {
            // 有依赖：triggerTime = 0，依赖满足即执行
            return 0;
        } else {
            // 无依赖（入口 job）：triggerTime = workflow.triggerTime
            return workflowTriggerTime;
        }
    }

    /**
     * 查找工作流中的入口任务（没有上游依赖的任务）
     */
    private Set<Long> findEntryJobIds(List<JobInfo> jobDefinitions, List<JobDependency> dependencies) {
        // 收集所有有上游依赖的任务 ID
        Set<Long> hasParentJobIds = new HashSet<>();
        for (JobDependency dep : dependencies) {
            hasParentJobIds.add(dep.getJobId());
        }

        // 找出没有上游依赖的任务
        Set<Long> entryJobIds = new HashSet<>();
        for (JobInfo job : jobDefinitions) {
            if (!hasParentJobIds.contains(job.getId())) {
                entryJobIds.add(job.getId());
            }
        }

        return entryJobIds;
    }

    // ==================== 告警相关 ====================

    @Override
    public void triggerAlert(long jobId, long jobRunId, long workflowRunId,
                             long namespaceId, String jobName, JobStatus status, String message) {
        // 确定触发事件类型
        AlarmTriggerEvent triggerEvent;
        if (status == JobStatus.TIMEOUT) {
            triggerEvent = AlarmTriggerEvent.TIMEOUT;
        } else if (status == JobStatus.FAIL) {
            triggerEvent = AlarmTriggerEvent.FAIL;
        } else if (status == JobStatus.SUCCESS) {
            triggerEvent = AlarmTriggerEvent.SUCCESS;
        } else {
            return;
        }

        log.info("触发告警: jobRunId={}, jobName={}, status={}", jobRunId, jobName, status);

        // 从缓存获取告警规则，未命中则查询 DB
        List<JobAlertRule> rules = alertRuleCache.get(jobId, id -> alertMapper.selectRulesByJobId(id));
        if (rules == null || rules.isEmpty()) {
            log.debug("没有配置告警规则: jobId={}", jobId);
            return;
        }

        for (JobAlertRule rule : rules) {
            // 检查触发事件是否匹配
            if (rule.getTriggerEvent() != null && rule.getTriggerEvent() != triggerEvent.getCode()) {
                continue;
            }

            // 构建告警内容
            String title = buildAlertTitle(jobName, status);
            String content = buildAlertContent(jobName, jobRunId, workflowRunId, status, message);

            // 异步发送告警
            asyncExecutor.submit(() -> sendAlertAsync(
                    namespaceId, workflowRunId, jobRunId, rule, title, content));
        }
    }

    /**
     * 异步发送告警
     */
    private void sendAlertAsync(long namespaceId, long workflowRunId, long jobRunId,
                                JobAlertRule rule, String title, String content) {
        try {
            // 发送告警
            AlertResult result = alertSenderService.send(
                    rule.getChannelType(),
                    rule.getChannelConfig(),
                    title,
                    content
            );

            // 发送完成后插入告警记录
            int sendStatus = result.success() ? 1 : 2; // 1=成功, 2=失败
            alertMapper.insertAlertLog(
                    namespaceId,
                    workflowRunId,
                    jobRunId,
                    rule.getRuleId(),
                    rule.getAlertChannelId(),
                    1, // 1=告警
                    title,
                    content,
                    sendStatus,
                    result.message()
            );

            if (result.success()) {
                log.info("告警发送成功: ruleId={}, channelType={}", rule.getRuleId(), rule.getChannelType());
            } else {
                log.warn("告警发送失败: ruleId={}, channelType={}, reason={}",
                        rule.getRuleId(), rule.getChannelType(), result.message());
            }
        } catch (Exception e) {
            log.error("告警发送异常: ruleId={}", rule.getRuleId(), e);
        }
    }

    /**
     * 构建告警标题
     */
    private String buildAlertTitle(String jobName, JobStatus status) {
        String statusText;
        if (status == JobStatus.TIMEOUT) {
            statusText = "超时";
        } else if (status == JobStatus.FAIL) {
            statusText = "失败";
        } else {
            statusText = "成功";
        }
        return String.format("[Datapillar] 任务%s: %s", statusText, jobName);
    }

    /**
     * 构建告警内容
     */
    private String buildAlertContent(String jobName, long jobRunId, long workflowRunId,
                                     JobStatus status, String message) {
        return String.format("""
                任务名称: %s
                任务实例ID: %d
                工作流实例ID: %d
                执行状态: %s
                错误信息: %s
                时间: %s
                """,
                jobName,
                jobRunId,
                workflowRunId,
                status.getDesc(),
                message != null ? message : "无",
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }
}
