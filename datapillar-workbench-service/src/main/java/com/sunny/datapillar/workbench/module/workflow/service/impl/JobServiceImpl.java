package com.sunny.datapillar.workbench.module.workflow.service.impl;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.workbench.module.workflow.dto.JobDto;
import com.sunny.datapillar.workbench.module.workflow.entity.JobComponent;
import com.sunny.datapillar.workbench.module.workflow.entity.JobInfo;
import com.sunny.datapillar.workbench.module.workflow.entity.JobWorkflow;
import com.sunny.datapillar.workbench.module.workflow.mapper.JobComponentMapper;
import com.sunny.datapillar.workbench.module.workflow.mapper.JobDependencyMapper;
import com.sunny.datapillar.workbench.module.workflow.mapper.JobInfoMapper;
import com.sunny.datapillar.workbench.module.workflow.mapper.JobWorkflowMapper;
import com.sunny.datapillar.workbench.module.workflow.service.JobService;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务服务实现
 *
 * @author sunny
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
    public JobDto.Response getJobDetail(Long id) {
        JobDto.Response job = jobInfoMapper.selectJobDetail(id);
        if (job == null) {
            throw new BusinessException(ErrorCode.ADMIN_JOB_NOT_FOUND, id);
        }
        return job;
    }

    @Override
    @Transactional
    public Long createJob(Long workflowId, JobDto.Create dto) {
        // 验证工作流
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, workflowId);
        }

        // 验证组件类型
        JobComponent component = componentMapper.selectById(dto.getJobType());
        if (component == null) {
            throw new BusinessException(ErrorCode.ADMIN_JOB_TYPE_NOT_FOUND, dto.getJobType());
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
    public void updateJob(Long id, JobDto.Update dto) {
        JobInfo jobInfo = jobInfoMapper.selectById(id);
        if (jobInfo == null) {
            throw new BusinessException(ErrorCode.ADMIN_JOB_NOT_FOUND, id);
        }

        if (dto.getJobName() != null) {
            jobInfo.setJobName(dto.getJobName());
        }
        if (dto.getJobType() != null) {
            JobComponent component = componentMapper.selectById(dto.getJobType());
            if (component == null) {
                throw new BusinessException(ErrorCode.ADMIN_JOB_TYPE_NOT_FOUND, dto.getJobType());
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
    public void deleteJob(Long id) {
        JobInfo jobInfo = jobInfoMapper.selectById(id);
        if (jobInfo == null) {
            throw new BusinessException(ErrorCode.ADMIN_JOB_NOT_FOUND, id);
        }

        // 删除相关依赖
        dependencyMapper.deleteByJobId(id);
        // 删除任务
        jobInfoMapper.deleteById(id);

        log.info("Deleted job: id={}", id);
    }

    @Override
    @Transactional
    public void updateJobPositions(Long workflowId, JobDto.LayoutSave dto) {
        // 验证工作流
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, workflowId);
        }

        jobInfoMapper.batchUpdatePositions(dto.getPositions());
        log.info("Updated job positions: workflowId={}, count={}", workflowId, dto.getPositions().size());
    }
}
