package com.sunny.job.worker.service;

import com.sunny.job.core.cron.CronUtils;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.TriggerType;
import com.sunny.job.worker.domain.entity.JobDependency;
import com.sunny.job.worker.domain.entity.JobInfo;
import com.sunny.job.worker.domain.entity.JobRun;
import com.sunny.job.worker.domain.entity.JobWorkflow;
import com.sunny.job.worker.domain.entity.JobWorkflowRun;
import com.sunny.job.worker.domain.mapper.JobDependencyMapper;
import com.sunny.job.worker.domain.mapper.JobInfoMapper;
import com.sunny.job.worker.domain.mapper.JobRunMapper;
import com.sunny.job.worker.domain.mapper.JobWorkflowMapper;
import com.sunny.job.worker.domain.mapper.JobWorkflowRunMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流恢复服务
 * <p>
 * 服务重启时检查并恢复中断的调度：
 * 1. 检查状态为 RUNNING 且 nextTriggerTime 有值的 workflow_run
 * 2. 如果下一个 workflow_run 尚未生成，则根据 nextTriggerTime 生成
 * <p>
 * 这确保了即使服务在任务执行过程中挂了，下一个周期的任务也不会丢失
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Service
public class WorkflowRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRecoveryService.class);

    @Value("${datapillar.job.worker.bucket-count:1024}")
    private int bucketCount;

    private final JobWorkflowRunMapper workflowRunMapper;
    private final JobWorkflowMapper workflowMapper;
    private final JobInfoMapper jobInfoMapper;
    private final JobRunMapper jobRunMapper;
    private final JobDependencyMapper dependencyMapper;

    public WorkflowRecoveryService(JobWorkflowRunMapper workflowRunMapper,
                                   JobWorkflowMapper workflowMapper,
                                   JobInfoMapper jobInfoMapper,
                                   JobRunMapper jobRunMapper,
                                   JobDependencyMapper dependencyMapper) {
        this.workflowRunMapper = workflowRunMapper;
        this.workflowMapper = workflowMapper;
        this.jobInfoMapper = jobInfoMapper;
        this.jobRunMapper = jobRunMapper;
        this.dependencyMapper = dependencyMapper;
    }

    /**
     * 服务启动时执行恢复检查
     */
    @PostConstruct
    public void onStartup() {
        log.info("开始执行工作流恢复检查...");
        try {
            recoverIncompleteWorkflows();
            log.info("工作流恢复检查完成");
        } catch (Exception e) {
            log.error("工作流恢复检查失败", e);
        }
    }

    /**
     * 恢复未完成的工作流
     * <p>
     * 查找状态为 RUNNING 且 nextTriggerTime 有值的 workflow_run，
     * 检查下一个 workflow_run 是否已生成，如果没有则生成
     */
    @Transactional(rollbackFor = Exception.class)
    public void recoverIncompleteWorkflows() {
        // 1. 查询需要恢复的 workflow_run
        List<JobWorkflowRun> runningWorkflowRuns = workflowRunMapper.selectRunningWithNextTriggerTime();

        if (runningWorkflowRuns == null || runningWorkflowRuns.isEmpty()) {
            log.info("没有需要恢复的工作流");
            return;
        }

        log.info("发现 {} 个需要检查的 workflow_run", runningWorkflowRuns.size());

        int recoveredCount = 0;
        for (JobWorkflowRun workflowRun : runningWorkflowRuns) {
            try {
                if (recoverSingleWorkflow(workflowRun)) {
                    recoveredCount++;
                }
            } catch (Exception e) {
                log.error("恢复 workflow_run 失败: workflowRunId={}", workflowRun.getId(), e);
            }
        }

        log.info("工作流恢复完成: 检查数={}, 恢复数={}", runningWorkflowRuns.size(), recoveredCount);
    }

    /**
     * 恢复单个工作流
     *
     * @param workflowRun 需要恢复的 workflow_run
     * @return 是否生成了新的 workflow_run
     */
    private boolean recoverSingleWorkflow(JobWorkflowRun workflowRun) {
        Long nextTriggerTime = workflowRun.getNextTriggerTime();
        if (nextTriggerTime == null || nextTriggerTime <= 0) {
            return false;
        }

        Long workflowId = workflowRun.getWorkflowId();

        // 检查是否已存在下一个 workflow_run
        boolean exists = workflowRunMapper.existsByWorkflowIdAndTriggerTime(workflowId, nextTriggerTime);
        if (exists) {
            log.debug("下一个 workflow_run 已存在，跳过: workflowId={}, nextTriggerTime={}",
                    workflowId, nextTriggerTime);
            return false;
        }

        // 查询 workflow 定义
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            log.debug("工作流不存在，跳过: workflowId={}", workflowId);
            return false;
        }

        // 检查触发类型
        TriggerType triggerType = TriggerType.of(workflow.getTriggerType());
        if (triggerType == TriggerType.MANUAL || triggerType == TriggerType.API) {
            return false;
        }

        log.info("恢复生成下一个 workflow_run: workflowId={}, nextTriggerTime={}",
                workflowId, CronUtils.formatTimestamp(nextTriggerTime));

        // 生成新的 workflow_run
        return generateWorkflowRun(workflow, nextTriggerTime);
    }

    /**
     * 生成 workflow_run 及其 job_run
     */
    private boolean generateWorkflowRun(JobWorkflow workflow, long triggerTime) {
        // 1. 创建 workflow_run
        JobWorkflowRun workflowRun = new JobWorkflowRun();
        workflowRun.setNamespaceId(workflow.getNamespaceId());
        workflowRun.setWorkflowId(workflow.getId());
        workflowRun.setTriggerType(workflow.getTriggerType());
        workflowRun.setTriggerTime(triggerTime);
        workflowRun.setStatus(JobStatus.WAITING.getCode());
        workflowRunMapper.insert(workflowRun);

        Long workflowRunId = workflowRun.getId();

        // 2. 查询任务定义
        List<JobInfo> jobs = jobInfoMapper.selectByWorkflowId(workflow.getId());
        if (jobs.isEmpty()) {
            log.warn("工作流无可用任务: workflowId={}", workflow.getId());
            return false;
        }

        // 3. 查询依赖关系
        List<JobDependency> dependencies = dependencyMapper.selectByWorkflowId(workflow.getId());
        Set<Long> jobsWithDependency = new HashSet<>();
        for (JobDependency dep : dependencies) {
            jobsWithDependency.add(dep.getJobId());
        }

        // 4. 创建 job_run
        List<JobRun> jobRuns = new ArrayList<>();
        Map<Long, JobRun> jobIdToRunMap = new HashMap<>();

        for (JobInfo job : jobs) {
            JobRun jobRun = new JobRun();
            jobRun.setNamespaceId(workflow.getNamespaceId());
            jobRun.setWorkflowRunId(workflowRunId);
            jobRun.setJobId(job.getId());
            jobRun.setBucketId((int) (job.getId() % bucketCount));
            jobRun.setStatus(JobStatus.WAITING.getCode());
            jobRun.setPriority(job.getPriority());

            // 计算 triggerTime
            long jobTriggerTime = calculateJobTriggerTime(job, workflow, triggerTime, jobsWithDependency.contains(job.getId()));
            jobRun.setTriggerTime(jobTriggerTime);

            // 设置 triggerType
            if (job.getTriggerType() != null) {
                jobRun.setTriggerType(job.getTriggerType());
            } else {
                jobRun.setTriggerType(workflow.getTriggerType());
            }

            jobRuns.add(jobRun);
            jobIdToRunMap.put(job.getId(), jobRun);
        }

        jobRunMapper.batchInsert(jobRuns);

        // 5. 创建依赖关系（简化版，不处理跨工作流依赖）
        for (JobDependency dep : dependencies) {
            JobRun childRun = jobIdToRunMap.get(dep.getJobId());
            JobRun parentRun = jobIdToRunMap.get(dep.getParentJobId());

            if (childRun != null && parentRun != null) {
                dependencyMapper.insertRunDependency(workflowRunId, childRun.getId(), parentRun.getId());
            }
        }

        log.info("恢复生成 workflow_run 成功: workflowRunId={}, jobRunCount={}", workflowRunId, jobRuns.size());
        return true;
    }

    /**
     * 计算 job_run 的 triggerTime
     */
    private long calculateJobTriggerTime(JobInfo job, JobWorkflow workflow, long workflowTriggerTime, boolean hasDependency) {
        // job 有独立触发配置
        if (job.getTriggerType() != null && job.getTriggerValue() != null) {
            TriggerType jobTriggerType = TriggerType.of(job.getTriggerType());
            return CronUtils.calculateNextTriggerTime(jobTriggerType, job.getTriggerValue(), workflowTriggerTime);
        }

        // 继承工作流触发配置
        if (hasDependency) {
            return 0;
        } else {
            return workflowTriggerTime;
        }
    }
}
