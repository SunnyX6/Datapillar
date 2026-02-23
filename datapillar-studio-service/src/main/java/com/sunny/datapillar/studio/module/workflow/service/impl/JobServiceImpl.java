package com.sunny.datapillar.studio.module.workflow.service.impl;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.studio.module.workflow.dto.JobDto;
import com.sunny.datapillar.studio.module.workflow.entity.JobComponent;
import com.sunny.datapillar.studio.module.workflow.entity.JobInfo;
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;
import com.sunny.datapillar.studio.module.workflow.mapper.JobComponentMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobDependencyMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobInfoMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobWorkflowMapper;
import com.sunny.datapillar.studio.module.workflow.service.JobService;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.NotFoundException;

/**
 * 任务服务实现
 * 实现任务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobInfoMapper jobInfoMapper;
    private final JobWorkflowMapper workflowMapper;
    private final JobComponentMapper componentMapper;
    private final JobDependencyMapper dependencyMapper;

    @Override
    public List<JobDto.Response> getJobsByWorkflowId(Long workflowId) {
        return jobInfoMapper.selectJobsByWorkflowId(workflowId);
    }

    @Override
    public JobDto.Response getJobDetail(Long workflowId, Long id) {
        getWorkflowJobOrThrow(workflowId, id);
        JobDto.Response job = jobInfoMapper.selectJobDetail(id);
        if (job == null) {
            throw new NotFoundException("任务不存在: workflowId=%s, jobId=%s", workflowId, id);
        }
        return job;
    }

    @Override
    @Transactional
    public Long createJob(Long workflowId, JobDto.Create dto) {
        // 验证工作流
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new NotFoundException("工作流不存在: workflowId=%s", workflowId);
        }

        // 验证组件类型
        JobComponent component = componentMapper.selectById(dto.getJobType());
        if (component == null) {
            throw new NotFoundException("任务类型不存在: jobType=%s", dto.getJobType());
        }

        JobInfo jobInfo = new JobInfo();
        BeanUtils.copyProperties(dto, jobInfo);
        jobInfo.setWorkflowId(workflowId);

        jobInfoMapper.insert(jobInfo);
        log.info("Created job: id={}, workflowId={}, name={}", jobInfo.getId(), workflowId, jobInfo.getJobName());
        return jobInfo.getId();
    }

    @Override
    @Transactional
    public void updateJob(Long workflowId, Long id, JobDto.Update dto) {
        JobInfo jobInfo = getWorkflowJobOrThrow(workflowId, id);

        if (dto.getJobName() != null) {
            jobInfo.setJobName(dto.getJobName());
        }
        if (dto.getJobType() != null) {
            JobComponent component = componentMapper.selectById(dto.getJobType());
            if (component == null) {
                throw new NotFoundException("任务类型不存在: jobType=%s", dto.getJobType());
            }
            jobInfo.setJobType(dto.getJobType());
        }
        if (dto.getJobParams() != null) {
            jobInfo.setJobParams(dto.getJobParams());
        }
        if (dto.getTimeoutSeconds() != null) {
            jobInfo.setTimeoutSeconds(dto.getTimeoutSeconds());
        }
        if (dto.getMaxRetryTimes() != null) {
            jobInfo.setMaxRetryTimes(dto.getMaxRetryTimes());
        }
        if (dto.getRetryInterval() != null) {
            jobInfo.setRetryInterval(dto.getRetryInterval());
        }
        if (dto.getPriority() != null) {
            jobInfo.setPriority(dto.getPriority());
        }
        if (dto.getPositionX() != null) {
            jobInfo.setPositionX(dto.getPositionX());
        }
        if (dto.getPositionY() != null) {
            jobInfo.setPositionY(dto.getPositionY());
        }
        if (dto.getDescription() != null) {
            jobInfo.setDescription(dto.getDescription());
        }

        jobInfoMapper.updateById(jobInfo);
        log.info("Updated job: id={}", id);
    }

    @Override
    @Transactional
    public void deleteJob(Long workflowId, Long id) {
        getWorkflowJobOrThrow(workflowId, id);

        // 删除相关依赖
        dependencyMapper.deleteByJobId(id);
        // 删除任务
        jobInfoMapper.deleteById(id);

        log.info("Deleted job: id={}", id);
    }

    private JobInfo getWorkflowJobOrThrow(Long workflowId, Long jobId) {
        JobInfo jobInfo = jobInfoMapper.selectById(jobId);
        if (jobInfo == null || !workflowId.equals(jobInfo.getWorkflowId())) {
            throw new NotFoundException("任务不存在: workflowId=%s, jobId=%s", workflowId, jobId);
        }
        return jobInfo;
    }

    @Override
    @Transactional
    public void updateJobPositions(Long workflowId, JobDto.LayoutSave dto) {
        // 验证工作流
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new NotFoundException("工作流不存在: workflowId=%s", workflowId);
        }

        jobInfoMapper.batchUpdatePositions(dto.getPositions());
        log.info("Updated job positions: workflowId={}, count={}", workflowId, dto.getPositions().size());
    }
}
