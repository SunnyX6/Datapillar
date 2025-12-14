package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.core.cron.CronUtils;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.TriggerType;
import com.sunny.job.server.entity.*;
import com.sunny.job.server.mapper.*;
import com.sunny.job.server.service.JobWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流定义 Service 实现
 * <p>
 * Server 核心职责：工作流上线时创建首个执行实例
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobWorkflowServiceImpl extends ServiceImpl<JobWorkflowMapper, JobWorkflow>
        implements JobWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(JobWorkflowServiceImpl.class);

    private final JobInfoMapper jobInfoMapper;
    private final JobDependencyMapper jobDependencyMapper;
    private final JobWorkflowRunMapper workflowRunMapper;
    private final JobRunMapper jobRunMapper;
    private final JobRunDependencyMapper jobRunDependencyMapper;

    public JobWorkflowServiceImpl(JobInfoMapper jobInfoMapper,
                                   JobDependencyMapper jobDependencyMapper,
                                   JobWorkflowRunMapper workflowRunMapper,
                                   JobRunMapper jobRunMapper,
                                   JobRunDependencyMapper jobRunDependencyMapper) {
        this.jobInfoMapper = jobInfoMapper;
        this.jobDependencyMapper = jobDependencyMapper;
        this.workflowRunMapper = workflowRunMapper;
        this.jobRunMapper = jobRunMapper;
        this.jobRunDependencyMapper = jobRunDependencyMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void online(Long workflowId) {
        log.info("上线工作流: workflowId={}", workflowId);

        // 1. 查询工作流
        JobWorkflow workflow = getById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        if (workflow.isOnline()) {
            log.warn("工作流已上线: workflowId={}", workflowId);
            return;
        }

        // 2. 计算下次触发时间
        long triggerTime = calculateNextTriggerTime(workflow);
        if (triggerTime <= 0) {
            throw new IllegalStateException("无法计算触发时间，请检查触发配置");
        }

        // 3. 创建首个执行实例
        createWorkflowRun(workflow, triggerTime);

        // 4. 更新工作流状态为上线
        workflow.setWorkflowStatus(1);
        updateById(workflow);

        log.info("工作流上线成功: workflowId={}, triggerTime={}", workflowId, triggerTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long workflowId) {
        log.info("下线工作流: workflowId={}", workflowId);

        JobWorkflow workflow = getById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        workflow.setWorkflowStatus(0);
        updateById(workflow);

        log.info("工作流下线成功: workflowId={}", workflowId);
    }

    /**
     * 计算下次触发时间
     */
    private long calculateNextTriggerTime(JobWorkflow workflow) {
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
            default -> -1;
        };
    }

    /**
     * 创建工作流执行实例（核心逻辑）
     * <p>
     * 把设计阶段的"蓝图"转换成执行阶段的"实例"：
     * - 1 条 job_workflow_run
     * - N 条 job_run（对应 workflow 内所有 job_info）
     * - M 条 job_run_dependency（根据 job_dependency 映射）
     */
    private void createWorkflowRun(JobWorkflow workflow, long triggerTime) {
        // 1. 创建 workflow_run
        JobWorkflowRun workflowRun = new JobWorkflowRun();
        workflowRun.setNamespaceId(workflow.getNamespaceId());
        workflowRun.setWorkflowId(workflow.getId());
        workflowRun.setTriggerType(workflow.getTriggerType());
        workflowRun.setTriggerTime(triggerTime);
        workflowRun.setStatus(JobStatus.WAITING.getCode());
        workflowRunMapper.insert(workflowRun);

        Long workflowRunId = workflowRun.getId();
        log.debug("创建 workflow_run: id={}", workflowRunId);

        // 2. 查询工作流下所有任务
        List<JobInfo> jobs = jobInfoMapper.selectList(
                new LambdaQueryWrapper<JobInfo>()
                        .eq(JobInfo::getWorkflowId, workflow.getId())
                        .eq(JobInfo::getIsDeleted, 0)
                        .eq(JobInfo::getJobStatus, 1)
        );

        if (jobs.isEmpty()) {
            log.warn("工作流无可用任务: workflowId={}", workflow.getId());
            return;
        }

        // 3. 创建 job_run，并记录 job_id → job_run_id 映射
        Map<Long, Long> jobIdToRunId = new HashMap<>();
        for (JobInfo job : jobs) {
            JobRun jobRun = new JobRun();
            jobRun.setNamespaceId(job.getNamespaceId());
            jobRun.setWorkflowRunId(workflowRunId);
            jobRun.setJobId(job.getId());
            jobRun.setTriggerType(workflow.getTriggerType());
            jobRun.setTriggerTime(triggerTime);
            jobRun.setExecutorParams(job.getJobParams());
            jobRun.setStatus(JobStatus.WAITING.getCode());
            jobRun.setPriority(job.getPriority());
            jobRun.setRetryCount(0);
            jobRunMapper.insert(jobRun);

            jobIdToRunId.put(job.getId(), jobRun.getId());
            log.debug("创建 job_run: jobId={}, jobRunId={}", job.getId(), jobRun.getId());
        }

        // 4. 查询依赖关系并创建 job_run_dependency
        List<JobDependency> dependencies = jobDependencyMapper.selectList(
                new LambdaQueryWrapper<JobDependency>()
                        .eq(JobDependency::getWorkflowId, workflow.getId())
        );

        for (JobDependency dep : dependencies) {
            Long jobRunId = jobIdToRunId.get(dep.getJobId());
            Long parentRunId = jobIdToRunId.get(dep.getParentJobId());

            if (jobRunId != null && parentRunId != null) {
                JobRunDependency runDep = new JobRunDependency();
                runDep.setWorkflowRunId(workflowRunId);
                runDep.setJobRunId(jobRunId);
                runDep.setParentRunId(parentRunId);
                jobRunDependencyMapper.insert(runDep);

                log.debug("创建 job_run_dependency: jobRunId={}, parentRunId={}", jobRunId, parentRunId);
            }
        }

        log.info("创建执行实例完成: workflowRunId={}, jobRunCount={}, dependencyCount={}",
                workflowRunId, jobs.size(), dependencies.size());
    }
}
