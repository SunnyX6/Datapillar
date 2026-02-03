package com.sunny.datapillar.studio.module.workflow.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.studio.module.workflow.dag.DagBuilder;
import com.sunny.datapillar.studio.module.workflow.dag.DagValidationException;
import com.sunny.datapillar.studio.module.workflow.dto.JobDependencyDto;
import com.sunny.datapillar.studio.module.workflow.dto.JobDto;
import com.sunny.datapillar.studio.module.workflow.entity.JobDependency;
import com.sunny.datapillar.studio.module.workflow.entity.JobInfo;
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;
import com.sunny.datapillar.studio.module.workflow.mapper.JobDependencyMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobInfoMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobWorkflowMapper;
import com.sunny.datapillar.studio.module.workflow.service.JobDependencyService;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务依赖服务实现
 *
 * @author sunny
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDependencyServiceImpl implements JobDependencyService {

    private final JobDependencyMapper dependencyMapper;
    private final JobInfoMapper jobInfoMapper;
    private final JobWorkflowMapper workflowMapper;

    @Override
    public List<JobDependencyDto.Response> getDependenciesByWorkflowId(Long workflowId) {
        return dependencyMapper.selectByWorkflowId(workflowId);
    }

    @Override
    public List<JobDependencyDto.Response> getDependenciesByJobId(Long jobId) {
        return dependencyMapper.selectByJobId(jobId);
    }

    @Override
    @Transactional
    public Long createDependency(Long workflowId, JobDependencyDto.Create dto) {
        // 验证工作流
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, workflowId);
        }

        // 验证任务
        JobInfo job = jobInfoMapper.selectById(dto.getJobId());
        if (job == null || !job.getWorkflowId().equals(workflowId)) {
            throw new BusinessException(ErrorCode.ADMIN_JOB_NOT_FOUND, dto.getJobId());
        }

        JobInfo parentJob = jobInfoMapper.selectById(dto.getParentJobId());
        if (parentJob == null || !parentJob.getWorkflowId().equals(workflowId)) {
            throw new BusinessException(ErrorCode.ADMIN_JOB_NOT_FOUND, dto.getParentJobId());
        }

        // 检查依赖是否已存在
        if (dependencyMapper.existsDependency(dto.getJobId(), dto.getParentJobId()) > 0) {
            throw new BusinessException(ErrorCode.ADMIN_DEPENDENCY_EXISTS);
        }

        // 验证添加后不会产生循环依赖
        validateNoCycle(workflowId, dto.getJobId(), dto.getParentJobId());

        JobDependency dependency = new JobDependency();
        dependency.setWorkflowId(workflowId);
        dependency.setJobId(dto.getJobId());
        dependency.setParentJobId(dto.getParentJobId());

        dependencyMapper.insert(dependency);
        log.info("Created dependency: workflowId={}, jobId={}, parentJobId={}", workflowId, dto.getJobId(), dto.getParentJobId());
        return dependency.getId();
    }

    @Override
    @Transactional
    public void deleteDependency(Long jobId, Long parentJobId) {
        int deleted = dependencyMapper.deleteDependency(jobId, parentJobId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.ADMIN_DEPENDENCY_NOT_FOUND);
        }
        log.info("Deleted dependency: jobId={}, parentJobId={}", jobId, parentJobId);
    }

    /**
     * 验证添加依赖后不会产生循环
     */
    private void validateNoCycle(Long workflowId, Long jobId, Long parentJobId) {
        // 获取工作流下所有任务
        List<JobDto.Response> jobs = jobInfoMapper.selectJobsByWorkflowId(workflowId);
        // 获取现有依赖
        List<JobDependencyDto.Response> dependencies = dependencyMapper.selectByWorkflowId(workflowId);

        DagBuilder dagBuilder = new DagBuilder();

        // 添加所有节点
        for (JobDto.Response job : jobs) {
            dagBuilder.addNode(job.getId());
        }

        // 添加现有边
        for (JobDependencyDto.Response dep : dependencies) {
            dagBuilder.addEdge(dep.getParentJobId(), dep.getJobId());
        }

        // 添加新边
        dagBuilder.addEdge(parentJobId, jobId);

        // 验证无环
        try {
            dagBuilder.validate();
        } catch (DagValidationException e) {
            throw new BusinessException(ErrorCode.ADMIN_DAG_HAS_CYCLE);
        }
    }
}
