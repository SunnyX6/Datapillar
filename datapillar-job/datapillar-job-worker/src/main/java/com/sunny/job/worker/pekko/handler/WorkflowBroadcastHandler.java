package com.sunny.job.worker.pekko.handler;

import com.sunny.job.core.cron.CronUtils;
import com.sunny.job.core.enums.BlockStrategy;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.RouteStrategy;
import com.sunny.job.core.enums.TriggerType;
import com.sunny.job.core.enums.WorkflowOp;
import com.sunny.job.core.enums.WorkflowRunOp;
import com.sunny.job.core.id.IdGenerator;
import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.core.message.SchedulerMessage;
import com.sunny.job.core.message.WorkflowBroadcast;
import com.sunny.job.core.message.WorkflowBroadcast.*;
import com.sunny.job.worker.domain.entity.JobInfo;
import com.sunny.job.worker.domain.entity.JobRun;
import com.sunny.job.worker.domain.entity.JobRunDependency;
import com.sunny.job.worker.domain.entity.JobWorkflow;
import com.sunny.job.worker.domain.entity.JobWorkflowRun;
import com.sunny.job.worker.domain.mapper.JobDependencyMapper;
import com.sunny.job.worker.domain.mapper.JobInfoMapper;
import com.sunny.job.worker.domain.mapper.JobRunMapper;
import com.sunny.job.worker.domain.mapper.JobWorkflowMapper;
import com.sunny.job.worker.domain.mapper.JobWorkflowRunMapper;
import com.sunny.job.worker.pekko.actor.JobSchedulerManager;
import com.sunny.job.worker.pekko.ddata.BucketManager;
import com.sunny.job.worker.pekko.ddata.MaxJobRunIdState;
import com.sunny.job.worker.pekko.ddata.WorkflowBroadcastState;
import com.sunny.job.worker.service.JobComponentCacheService;
import com.sunny.job.worker.service.JobInfoCacheService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流运行实例广播处理器
 * <p>
 * 监听 Server 广播的工作流事件，根据 Bucket 归属创建 run
 * <p>
 * 支持的 Op 类型：
 * - ONLINE: 上线，刷新缓存 + 创建 workflow_run + job_run + job_run_dependency
 * - MANUAL_TRIGGER: 手动触发，刷新缓存 + 创建 workflow_run + job_run + job_run_dependency
 * - OFFLINE: 下线，取消/删除待执行的 run
 * - KILL: 终止，强制终止运行中的实例
 * - RERUN: 重跑，重置指定 job_run 状态
 * <p>
 * Bucket 归属规则：
 * - workflow_run 由 workflowId % bucketCount 的 Bucket Owner 创建
 * - job_run 由 jobId % bucketCount 的 Bucket Owner 创建
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@Component
public class WorkflowBroadcastHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowBroadcastHandler.class);

    @Value("${datapillar.job.worker.bucket-count:1024}")
    private int bucketCount;

    private final WorkflowBroadcastState broadcastState;
    private final BucketManager bucketManager;
    private final JobWorkflowMapper workflowMapper;
    private final JobInfoMapper jobInfoMapper;
    private final JobWorkflowRunMapper workflowRunMapper;
    private final JobRunMapper jobRunMapper;
    private final JobDependencyMapper dependencyMapper;
    private final MaxJobRunIdState maxJobRunIdState;
    private final JobSchedulerManager schedulerManager;
    private final TransactionTemplate transactionTemplate;
    private final JobInfoCacheService jobInfoCacheService;
    private final JobComponentCacheService componentCacheService;
    private final IdGenerator idGenerator;

    public WorkflowBroadcastHandler(WorkflowBroadcastState broadcastState,
                                     BucketManager bucketManager,
                                     JobWorkflowMapper workflowMapper,
                                     JobInfoMapper jobInfoMapper,
                                     JobWorkflowRunMapper workflowRunMapper,
                                     JobRunMapper jobRunMapper,
                                     JobDependencyMapper dependencyMapper,
                                     MaxJobRunIdState maxJobRunIdState,
                                     JobSchedulerManager schedulerManager,
                                     TransactionTemplate transactionTemplate,
                                     JobInfoCacheService jobInfoCacheService,
                                     JobComponentCacheService componentCacheService,
                                     IdGenerator idGenerator) {
        this.broadcastState = broadcastState;
        this.bucketManager = bucketManager;
        this.workflowMapper = workflowMapper;
        this.jobInfoMapper = jobInfoMapper;
        this.workflowRunMapper = workflowRunMapper;
        this.jobRunMapper = jobRunMapper;
        this.dependencyMapper = dependencyMapper;
        this.maxJobRunIdState = maxJobRunIdState;
        this.schedulerManager = schedulerManager;
        this.transactionTemplate = transactionTemplate;
        this.jobInfoCacheService = jobInfoCacheService;
        this.componentCacheService = componentCacheService;
        this.idGenerator = idGenerator;
    }

    @PostConstruct
    public void init() {
        broadcastState.subscribe(this::handleBroadcast);
        log.info("WorkflowBroadcastHandler 初始化完成，已订阅广播事件");
    }

    /**
     * 处理广播事件
     */
    private void handleBroadcast(WorkflowBroadcast event) {
        String eventId = event.getEventId();
        String op = event.getOp();
        log.info("处理广播事件: eventId={}, op={}, opLevel={}", eventId, op, event.getOpLevel());

        if (event.isWorkflowOp()) {
            WorkflowOp workflowOp = event.getWorkflowOp();
            if (workflowOp == null) {
                log.warn("未知的 WorkflowOp: {}", op);
                return;
            }
            switch (workflowOp) {
                case ONLINE, MANUAL_TRIGGER -> handleTrigger(eventId, event.getPayloadAs(TriggerPayload.class), workflowOp);
                case OFFLINE -> handleOffline(event.getPayloadAs(OfflinePayload.class));
            }
        } else if (event.isWorkflowRunOp()) {
            WorkflowRunOp workflowRunOp = event.getWorkflowRunOp();
            if (workflowRunOp == null) {
                log.warn("未知的 WorkflowRunOp: {}", op);
                return;
            }
            switch (workflowRunOp) {
                case KILL -> handleKill(event.getPayloadAs(KillPayload.class));
                case RERUN -> handleRerun(event.getPayloadAs(RerunPayload.class));
            }
        } else {
            log.warn("未知的 opLevel: {}", event.getOpLevel());
        }
    }

    /**
     * 处理触发事件
     * <p>
     * 使用确定性 hash 计算 runId：
     * - workflowRunId = IdGenerator.deterministicId(eventId, workflowId)
     * - jobRunId = IdGenerator.deterministicId(eventId, jobId)
     */
    private void handleTrigger(String eventId, TriggerPayload payload, WorkflowOp workflowOp) {
        Long workflowId = payload.workflowId();
        int workflowBucketId = (int) (workflowId % bucketCount);

        // 上线/手动触发时刷新该工作流下所有 job 的缓存，确保使用最新配置
        for (Long jobId : payload.jobIds()) {
            jobInfoCacheService.invalidate(jobId);
        }
        log.info("刷新缓存: workflowId={}, op={}, invalidatedJobCount={}", workflowId, workflowOp, payload.jobIds().size());

        // 使用确定性 hash 计算 workflowRunId（所有 Worker 计算结果一致）
        long workflowRunId = IdGenerator.deterministicId(eventId, workflowId);

        boolean isWorkflowOwner = bucketManager.hasBucket(workflowBucketId);

        // 查询 workflow 定义获取触发配置
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            log.warn("工作流不存在: workflowId={}", workflowId);
            return;
        }

        // 计算 workflow 的 triggerTime
        long workflowTriggerTime = calculateWorkflowTriggerTime(workflow);

        // 收集本 Worker 负责的 jobId，并计算对应的 jobRunId
        List<Long> myJobIds = new ArrayList<>();
        Map<Long, Long> jobIdToRunIdMap = new HashMap<>();
        Map<Long, JobInfo> jobInfoMap = new HashMap<>();
        Set<Long> myJobRunIds = new HashSet<>();

        for (Long jobId : payload.jobIds()) {
            int jobBucketId = (int) (jobId % bucketCount);
            // 所有 Worker 都计算所有 jobRunId（用于依赖关系）
            long jobRunId = IdGenerator.deterministicId(eventId, jobId);
            jobIdToRunIdMap.put(jobId, jobRunId);

            if (bucketManager.hasBucket(jobBucketId)) {
                myJobIds.add(jobId);
                myJobRunIds.add(jobRunId);
                // 查询 job 定义获取触发配置
                JobInfo jobInfo = jobInfoMapper.selectById(jobId);
                if (jobInfo != null) {
                    jobInfoMap.put(jobId, jobInfo);
                }
            }
        }

        if (myJobIds.isEmpty() && !isWorkflowOwner) {
            log.debug("本 Worker 不负责任何 job，跳过: workflowId={}", workflowId);
            return;
        }

        // 收集有依赖的 jobId
        Set<Long> jobsWithDependency = new HashSet<>();
        for (DependencyInfo dep : payload.dependencies()) {
            jobsWithDependency.add(dep.jobId());
        }

        // 计算每个 job 的 triggerTime（考虑 job 级别独立配置）
        Map<Long, Long> jobTriggerTimeMap = new HashMap<>();
        for (Long jobId : myJobIds) {
            JobInfo jobInfo = jobInfoMap.get(jobId);
            boolean hasDependency = jobsWithDependency.contains(jobId);
            long jobTriggerTime = calculateJobTriggerTime(jobInfo, workflow, workflowTriggerTime, hasDependency);
            jobTriggerTimeMap.put(jobId, jobTriggerTime);
        }

        // 使用 TransactionTemplate 确保事务生效
        transactionTemplate.executeWithoutResult(status -> {
            if (isWorkflowOwner) {
                createWorkflowRun(workflowRunId, payload, workflow, workflowTriggerTime, workflowOp);
                log.info("创建 workflow_run: workflowRunId={}, workflowId={}, triggerTime={}",
                        workflowRunId, workflowId, workflowTriggerTime);
            }

            createJobRuns(workflowRunId, payload, myJobIds, jobIdToRunIdMap, jobInfoMap, jobTriggerTimeMap, workflow, workflowOp);
            createJobRunDependencies(workflowRunId, payload, myJobRunIds, jobIdToRunIdMap);
        });

        // 广播 maxJobRunId
        if (!myJobRunIds.isEmpty()) {
            long maxJobRunId = myJobRunIds.stream().mapToLong(Long::longValue).max().orElse(0);
            if (maxJobRunId > 0) {
                maxJobRunIdState.updateMaxId(maxJobRunId);
            }
        }

        // 通知 Scheduler 注册新任务
        notifyScheduler(workflowRunId, payload.namespaceId(), workflowId, workflow,
                myJobIds, jobIdToRunIdMap, jobInfoMap, jobTriggerTimeMap, payload.dependencies());

        log.info("处理触发事件完成: workflowId={}, workflowRunId={}, myJobCount={}, isWorkflowOwner={}, triggerTime={}",
                workflowId, workflowRunId, myJobIds.size(), isWorkflowOwner, workflowTriggerTime);
    }

    /**
     * 计算 workflow 的 triggerTime
     */
    private long calculateWorkflowTriggerTime(JobWorkflow workflow) {
        TriggerType triggerType = TriggerType.of(workflow.getTriggerType());
        String triggerValue = workflow.getTriggerValue();
        long now = System.currentTimeMillis();

        return switch (triggerType) {
            case CRON -> CronUtils.calculateCronNextTrigger(triggerValue, now);
            case FIXED_RATE, FIXED_DELAY -> {
                long intervalMs = Long.parseLong(triggerValue) * 1000;
                yield now + intervalMs;
            }
            case MANUAL, API -> now;
            default -> now;
        };
    }

    /**
     * 计算 job_run 的 triggerTime
     * <p>
     * 优先使用 job 级别的独立触发配置，否则继承 workflow 配置
     */
    private long calculateJobTriggerTime(JobInfo job, JobWorkflow workflow, long workflowTriggerTime, boolean hasDependency) {
        // job 有独立触发配置
        if (job != null && job.getTriggerType() != null && job.getTriggerValue() != null) {
            TriggerType jobTriggerType = TriggerType.of(job.getTriggerType());
            return CronUtils.calculateNextTriggerTime(jobTriggerType, job.getTriggerValue(), workflowTriggerTime);
        }

        // 继承工作流触发配置
        if (hasDependency) {
            return 0;  // 有依赖的任务 triggerTime 设为 0，等依赖完成后再触发
        } else {
            return workflowTriggerTime;
        }
    }

    /**
     * 处理重跑事件
     */
    private void handleRerun(RerunPayload payload) {
        Long workflowId = payload.workflowId();
        Long workflowRunId = payload.workflowRunId();
        Map<Long, Long> jobRunIdToJobIdMap = payload.jobRunIdToJobIdMap();

        log.info("处理重跑事件: workflowId={}, workflowRunId={}, jobRunCount={}",
                workflowId, workflowRunId, jobRunIdToJobIdMap.size());

        // 找出本 Worker 负责的 jobRunId
        Map<Long, Long> myJobRunIdToJobIdMap = new HashMap<>();
        for (Map.Entry<Long, Long> entry : jobRunIdToJobIdMap.entrySet()) {
            Long jobRunId = entry.getKey();
            Integer bucketId = jobRunMapper.selectBucketIdById(jobRunId);
            if (bucketId != null && bucketManager.hasBucket(bucketId)) {
                myJobRunIdToJobIdMap.put(jobRunId, entry.getValue());
            }
        }

        if (!myJobRunIdToJobIdMap.isEmpty()) {
            // 重置状态为 WAITING
            jobRunMapper.batchUpdateStatusToWaiting(new ArrayList<>(myJobRunIdToJobIdMap.keySet()), WorkflowRunOp.RERUN.name());

            // 通知 Scheduler 重新注册
            for (Map.Entry<Long, Long> entry : myJobRunIdToJobIdMap.entrySet()) {
                Long jobRunId = entry.getKey();
                Long jobId = entry.getValue();
                Integer bucketId = jobRunMapper.selectBucketIdById(jobRunId);
                if (bucketId != null) {
                    SchedulerMessage.RegisterJob registerMsg = new SchedulerMessage.RegisterJob(
                            jobRunId, jobId, System.currentTimeMillis(), 0
                    );
                    schedulerManager.getSchedulerForBucket(bucketId).tell(registerMsg);
                }
            }

            log.info("重跑任务: workflowRunId={}, myJobRunCount={}", workflowRunId, myJobRunIdToJobIdMap.size());
        }
    }

    /**
     * 处理下线事件（强制下线）
     */
    private void handleOffline(OfflinePayload payload) {
        Long workflowId = payload.workflowId();

        log.info("处理下线事件: workflowId={}", workflowId);

        // 查询本 Worker 负责的 bucket
        Set<Integer> myBuckets = bucketManager.getMyBuckets();
        if (myBuckets.isEmpty()) {
            return;
        }

        log.info("下线工作流: workflowId={}, 需要取消本 Worker 负责的相关任务", workflowId);

        // 通知 Scheduler 移除该 workflow 的所有任务
        for (Integer bucketId : myBuckets) {
            SchedulerMessage.CancelWorkflow cancelMsg = new SchedulerMessage.CancelWorkflow(workflowId);
            schedulerManager.getSchedulerForBucket(bucketId).tell(cancelMsg);
        }
    }

    /**
     * 处理终止事件
     */
    private void handleKill(KillPayload payload) {
        Long workflowRunId = payload.workflowRunId();

        log.info("处理终止事件: workflowRunId={}", workflowRunId);

        // 查询本 Worker 负责的 bucket
        Set<Integer> myBuckets = bucketManager.getMyBuckets();
        if (myBuckets.isEmpty()) {
            return;
        }

        // 查询该 workflowRun 下本 Worker 负责的 job_run
        List<JobRunInfo> myJobRuns = jobRunMapper.selectByWorkflowRunIdAndBuckets(
                workflowRunId, myBuckets);

        if (myJobRuns.isEmpty()) {
            return;
        }

        // 收集需要取消的 jobRunId
        List<Long> jobRunIds = new ArrayList<>();
        for (JobRunInfo jobRun : myJobRuns) {
            jobRunIds.add(jobRun.getJobRunId());
        }

        // 更新状态为 CANCELLED
        jobRunMapper.batchUpdateStatusToCancelled(jobRunIds, WorkflowRunOp.KILL.name());

        // 通知 Scheduler 取消任务
        for (JobRunInfo jobRun : myJobRuns) {
            SchedulerMessage.CancelJob cancelMsg = new SchedulerMessage.CancelJob(jobRun.getJobRunId());
            schedulerManager.getSchedulerForBucket(jobRun.getBucketId()).tell(cancelMsg);
        }

        log.info("终止工作流实例: workflowRunId={}, cancelledCount={}", workflowRunId, jobRunIds.size());
    }

    /**
     * 创建 workflow_run
     */
    private void createWorkflowRun(long workflowRunId, TriggerPayload payload, JobWorkflow workflow, long triggerTime, WorkflowOp workflowOp) {
        JobWorkflowRun workflowRun = new JobWorkflowRun();
        workflowRun.setId(workflowRunId);
        workflowRun.setNamespaceId(payload.namespaceId());
        workflowRun.setWorkflowId(payload.workflowId());
        workflowRun.setTriggerType(workflow.getTriggerType());
        workflowRun.setTriggerTime(triggerTime);
        workflowRun.setStatus(JobStatus.WAITING.getCode());
        workflowRun.setOp(workflowOp.name());

        workflowRunMapper.insert(workflowRun);
    }

    /**
     * 创建 job_run
     */
    private void createJobRuns(long workflowRunId, TriggerPayload payload, List<Long> myJobIds,
                                Map<Long, Long> jobIdToRunIdMap, Map<Long, JobInfo> jobInfoMap,
                                Map<Long, Long> jobTriggerTimeMap, JobWorkflow workflow, WorkflowOp workflowOp) {
        List<JobRun> jobRuns = new ArrayList<>();

        for (Long jobId : myJobIds) {
            Long jobRunId = jobIdToRunIdMap.get(jobId);
            int bucketId = (int) (jobId % bucketCount);
            JobInfo jobInfo = jobInfoMap.get(jobId);
            Long triggerTime = jobTriggerTimeMap.get(jobId);

            JobRun jobRun = new JobRun();
            jobRun.setId(jobRunId);
            jobRun.setNamespaceId(payload.namespaceId());
            jobRun.setWorkflowRunId(workflowRunId);
            jobRun.setWorkflowId(payload.workflowId());
            jobRun.setJobId(jobId);
            jobRun.setBucketId(bucketId);
            jobRun.setStatus(JobStatus.WAITING.getCode());
            jobRun.setRetryCount(0);
            jobRun.setTriggerTime(triggerTime != null ? triggerTime : 0);

            // 设置 triggerType：优先用 job 级别，否则用 workflow 级别
            if (jobInfo != null && jobInfo.getTriggerType() != null) {
                jobRun.setTriggerType(jobInfo.getTriggerType());
            } else {
                jobRun.setTriggerType(workflow.getTriggerType());
            }

            // 设置 priority
            if (jobInfo != null && jobInfo.getPriority() != null) {
                jobRun.setPriority(jobInfo.getPriority());
            } else {
                jobRun.setPriority(workflow.getPriority());
            }

            // 设置操作类型
            jobRun.setOp(workflowOp.name());

            jobRuns.add(jobRun);
        }

        if (!jobRuns.isEmpty()) {
            jobRunMapper.batchInsert(jobRuns);
        }
    }

    /**
     * 创建 job_run_dependency
     */
    private void createJobRunDependencies(long workflowRunId, TriggerPayload payload,
                                           Set<Long> myJobRunIds, Map<Long, Long> jobIdToRunIdMap) {
        List<JobRunDependency> runDependencies = new ArrayList<>();

        for (DependencyInfo dep : payload.dependencies()) {
            // 使用 jobId 获取对应的 jobRunId
            Long jobRunId = jobIdToRunIdMap.get(dep.jobId());
            Long parentRunId = jobIdToRunIdMap.get(dep.parentJobId());

            if (jobRunId == null || parentRunId == null) {
                continue;
            }

            // 只创建本 Worker 负责的 job 的依赖关系
            if (!myJobRunIds.contains(jobRunId)) {
                continue;
            }

            JobRunDependency runDep = new JobRunDependency();
            runDep.setId(idGenerator.nextId());
            runDep.setWorkflowRunId(workflowRunId);
            runDep.setJobRunId(jobRunId);
            runDep.setParentRunId(parentRunId);
            runDependencies.add(runDep);
        }

        if (!runDependencies.isEmpty()) {
            dependencyMapper.batchInsertRunDependencies(runDependencies);
        }
    }

    /**
     * 通知 Scheduler 注册新任务
     */
    private void notifyScheduler(long workflowRunId, long namespaceId, long workflowId, JobWorkflow workflow,
                                  List<Long> myJobIds, Map<Long, Long> jobIdToRunIdMap,
                                  Map<Long, JobInfo> jobInfoMap, Map<Long, Long> jobTriggerTimeMap,
                                  List<DependencyInfo> dependencies) {
        // 构建 jobId -> parentJobRunIds 映射
        Map<Long, List<Long>> jobIdToParentRunIds = new HashMap<>();
        for (DependencyInfo dep : dependencies) {
            Long parentRunId = jobIdToRunIdMap.get(dep.parentJobId());
            if (parentRunId != null) {
                jobIdToParentRunIds.computeIfAbsent(dep.jobId(), k -> new ArrayList<>()).add(parentRunId);
            }
        }

        for (Long jobId : myJobIds) {
            Long jobRunId = jobIdToRunIdMap.get(jobId);
            Long triggerTime = jobTriggerTimeMap.get(jobId);
            JobInfo jobInfo = jobInfoMap.get(jobId);

            int priority = (jobInfo != null && jobInfo.getPriority() != null) ? jobInfo.getPriority() : 0;
            int bucketId = (int) (jobId % bucketCount);

            // 构建完整的 JobRunInfo
            JobRunInfo jobRunInfo = new JobRunInfo();
            jobRunInfo.setJobRunId(jobRunId);
            jobRunInfo.setWorkflowRunId(workflowRunId);
            jobRunInfo.setWorkflowId(workflowId);
            jobRunInfo.setJobId(jobId);
            jobRunInfo.setBucketId(bucketId);
            jobRunInfo.setNamespaceId(namespaceId);
            jobRunInfo.setTriggerTime(triggerTime != null ? triggerTime : 0);
            jobRunInfo.setPriority(priority);
            jobRunInfo.setStatus(JobStatus.WAITING);
            jobRunInfo.setRetryCount(0);

            // 设置 triggerType
            if (jobInfo != null && jobInfo.getTriggerType() != null) {
                jobRunInfo.setTriggerType(jobInfo.getTriggerType());
            } else {
                jobRunInfo.setTriggerType(workflow.getTriggerType());
            }

            // 从 jobInfo 设置执行相关信息
            if (jobInfo != null) {
                jobRunInfo.setJobName(jobInfo.getJobName());
                jobRunInfo.setJobType(componentCacheService.getComponentCode(jobInfo.getJobType()));
                jobRunInfo.setJobParams(jobInfo.getJobParams());
                jobRunInfo.setRouteStrategy(RouteStrategy.of(jobInfo.getRouteStrategy()));
                jobRunInfo.setBlockStrategy(BlockStrategy.of(jobInfo.getBlockStrategy()));
                jobRunInfo.setTimeoutSeconds(jobInfo.getTimeoutSeconds() != null ? jobInfo.getTimeoutSeconds() : 0);
                jobRunInfo.setMaxRetryTimes(jobInfo.getMaxRetryTimes() != null ? jobInfo.getMaxRetryTimes() : 0);
                jobRunInfo.setRetryInterval(jobInfo.getRetryInterval() != null ? jobInfo.getRetryInterval() : 0);
            }

            // 设置依赖关系（父任务的 jobRunId 列表）
            List<Long> parentRunIds = jobIdToParentRunIds.get(jobId);
            if (parentRunIds != null && !parentRunIds.isEmpty()) {
                jobRunInfo.setParentJobRunIds(parentRunIds);
            }

            SchedulerMessage.RegisterJob registerMsg = new SchedulerMessage.RegisterJob(
                    jobRunId, jobId, triggerTime != null ? triggerTime : 0, priority, jobRunInfo
            );
            schedulerManager.getSchedulerForBucket(bucketId).tell(registerMsg);
        }
    }
}
