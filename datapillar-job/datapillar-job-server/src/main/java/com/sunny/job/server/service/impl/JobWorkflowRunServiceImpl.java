package com.sunny.job.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.server.entity.JobRun;
import com.sunny.job.server.entity.JobWorkflowRun;
import com.sunny.job.server.mapper.JobRunMapper;
import com.sunny.job.server.mapper.JobWorkflowRunMapper;
import com.sunny.job.server.service.JobWorkflowRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作流执行实例 Service 实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class JobWorkflowRunServiceImpl extends ServiceImpl<JobWorkflowRunMapper, JobWorkflowRun>
        implements JobWorkflowRunService {

    private static final Logger log = LoggerFactory.getLogger(JobWorkflowRunServiceImpl.class);

    private final JobRunMapper jobRunMapper;

    public JobWorkflowRunServiceImpl(JobRunMapper jobRunMapper) {
        this.jobRunMapper = jobRunMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rerun(Long workflowRunId) {
        log.info("重跑工作流实例: workflowRunId={}", workflowRunId);

        // 1. 检查工作流实例是否存在
        JobWorkflowRun workflowRun = getById(workflowRunId);
        if (workflowRun == null) {
            throw new IllegalArgumentException("工作流实例不存在: " + workflowRunId);
        }

        // 2. 检查状态（只有失败或取消的才能重跑）
        JobStatus currentStatus = JobStatus.of(workflowRun.getStatus());
        if (!currentStatus.isTerminal()) {
            throw new IllegalStateException("只有已结束的工作流实例才能重跑");
        }

        // 3. 更新 workflow_run 状态为 RUNNING
        workflowRun.setStatus(JobStatus.RUNNING.getCode());
        workflowRun.setEndTime(null);
        updateById(workflowRun);

        // 4. 查询所有失败/超时/取消的 job_run
        List<JobRun> failedJobs = jobRunMapper.selectList(
                new LambdaQueryWrapper<JobRun>()
                        .eq(JobRun::getWorkflowRunId, workflowRunId)
                        .in(JobRun::getStatus,
                                JobStatus.FAIL.getCode(),
                                JobStatus.TIMEOUT.getCode(),
                                JobStatus.CANCEL.getCode())
        );

        // 5. 重置失败的 job_run 状态为 WAITING
        for (JobRun jobRun : failedJobs) {
            jobRunMapper.update(null,
                    new LambdaUpdateWrapper<JobRun>()
                            .eq(JobRun::getId, jobRun.getId())
                            .set(JobRun::getStatus, JobStatus.WAITING.getCode())
                            .set(JobRun::getRetryCount, 0)
                            .set(JobRun::getStartTime, null)
                            .set(JobRun::getEndTime, null)
                            .set(JobRun::getResultMessage, null)
            );
            log.debug("重置 job_run 状态: jobRunId={}", jobRun.getId());
        }

        log.info("重跑工作流实例完成: workflowRunId={}, 重置任务数={}", workflowRunId, failedJobs.size());
    }
}
