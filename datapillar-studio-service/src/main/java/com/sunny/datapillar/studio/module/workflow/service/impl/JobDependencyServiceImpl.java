package com.sunny.datapillar.studio.module.workflow.service.impl;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunny.datapillar.studio.module.workflow.service.dag.DagBuilder;
import com.sunny.datapillar.studio.module.workflow.service.dag.DagValidationException;
import com.sunny.datapillar.studio.module.workflow.entity.JobDependency;
import com.sunny.datapillar.studio.module.workflow.entity.JobInfo;
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;
import com.sunny.datapillar.studio.module.workflow.mapper.JobDependencyMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobInfoMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobWorkflowMapper;
import com.sunny.datapillar.studio.module.workflow.service.JobDependencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ConflictException;

/**
 * 任务Dependency服务实现
 * 实现任务Dependency业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDependencyServiceImpl implements JobDependencyService {

    private final JobDependencyMapper dependencyMapper;
    private final JobInfoMapper jobInfoMapper;
    private final JobWorkflowMapper workflowMapper;

    @Override
    public List<JobDependencyResponse> getDependenciesByWorkflowId(Long workflowId) {
        return dependencyMapper.selectByWorkflowId(workflowId);
    }

    @Override
    public List<JobDependencyResponse> getDependenciesByJobId(Long jobId) {
        return dependencyMapper.selectByJobId(jobId);
    }

    @Override
    @Transactional
    public Long createDependency(Long workflowId, JobDependencyCreateRequest dto) {
        // 验证工作流
        JobWorkflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("工作流不存在: workflowId=%s", workflowId);
        }

        // 验证任务
        JobInfo job = jobInfoMapper.selectById(dto.getJobId());
        if (job == null || !job.getWorkflowId().equals(workflowId)) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("任务不存在: jobId=%s", dto.getJobId());
        }

        JobInfo parentJob = jobInfoMapper.selectById(dto.getParentJobId());
        if (parentJob == null || !parentJob.getWorkflowId().equals(workflowId)) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("任务不存在: jobId=%s", dto.getParentJobId());
        }

        // 检查依赖是否已存在
        if (dependencyMapper.existsDependency(dto.getJobId(), dto.getParentJobId()) > 0) {
            throw new com.sunny.datapillar.common.exception.ConflictException("依赖关系已存在");
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
    public void deleteDependency(Long workflowId, Long jobId, Long parentJobId) {
        int deleted = dependencyMapper.deleteDependency(workflowId, jobId, parentJobId);
        if (deleted == 0) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("依赖关系不存在");
        }
        log.info("Deleted dependency: workflowId={}, jobId={}, parentJobId={}", workflowId, jobId, parentJobId);
    }

    /**
     * 验证添加依赖后不会产生循环
     */
    private void validateNoCycle(Long workflowId, Long jobId, Long parentJobId) {
        // 获取工作流下所有任务
        List<JobResponse> jobs = jobInfoMapper.selectJobsByWorkflowId(workflowId);
        // 获取现有依赖
        List<JobDependencyResponse> dependencies = dependencyMapper.selectByWorkflowId(workflowId);

        DagBuilder dagBuilder = new DagBuilder();

        // 添加所有节点
        for (JobResponse job : jobs) {
            dagBuilder.addNode(job.getId());
        }

        // 添加现有边
        for (JobDependencyResponse dep : dependencies) {
            dagBuilder.addEdge(dep.getParentJobId(), dep.getJobId());
        }

        // 添加新边
        dagBuilder.addEdge(parentJobId, jobId);

        // 验证无环
        try {
            dagBuilder.validate();
        } catch (DagValidationException e) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("工作流存在循环依赖");
        }
    }
}
