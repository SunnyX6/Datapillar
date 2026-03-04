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
import com.sunny.datapillar.studio.module.workflow.entity.JobDependency;
import com.sunny.datapillar.studio.module.workflow.entity.JobInfo;
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;
import com.sunny.datapillar.studio.module.workflow.mapper.JobDependencyMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobInfoMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobWorkflowMapper;
import com.sunny.datapillar.studio.module.workflow.service.JobDependencyService;
import com.sunny.datapillar.studio.module.workflow.service.dag.DagBuilder;
import com.sunny.datapillar.studio.module.workflow.service.dag.DagValidationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TaskDependencyService implementation achieve tasksDependencyBusiness process and rule
 * verification
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
    // Validation workflow
    JobWorkflow workflow = workflowMapper.selectById(workflowId);
    if (workflow == null) {
      throw new com.sunny.datapillar.common.exception.NotFoundException(
          "Workflow does not exist: workflowId=%s", workflowId);
    }

    // Verification tasks
    JobInfo job = jobInfoMapper.selectById(dto.getJobId());
    if (job == null || !job.getWorkflowId().equals(workflowId)) {
      throw new com.sunny.datapillar.common.exception.NotFoundException(
          "Task does not exist: jobId=%s", dto.getJobId());
    }

    JobInfo parentJob = jobInfoMapper.selectById(dto.getParentJobId());
    if (parentJob == null || !parentJob.getWorkflowId().equals(workflowId)) {
      throw new com.sunny.datapillar.common.exception.NotFoundException(
          "Task does not exist: jobId=%s", dto.getParentJobId());
    }

    // Check if dependencies already exist
    if (dependencyMapper.existsDependency(dto.getJobId(), dto.getParentJobId()) > 0) {
      throw new com.sunny.datapillar.common.exception.ConflictException(
          "Dependency already exists");
    }

    // Verify that no circular dependencies will be generated after adding
    validateNoCycle(workflowId, dto.getJobId(), dto.getParentJobId());

    JobDependency dependency = new JobDependency();
    dependency.setWorkflowId(workflowId);
    dependency.setJobId(dto.getJobId());
    dependency.setParentJobId(dto.getParentJobId());

    dependencyMapper.insert(dependency);
    log.info(
        "Created dependency: workflowId={}, jobId={}, parentJobId={}",
        workflowId,
        dto.getJobId(),
        dto.getParentJobId());
    return dependency.getId();
  }

  @Override
  @Transactional
  public void deleteDependency(Long workflowId, Long jobId, Long parentJobId) {
    int deleted = dependencyMapper.deleteDependency(workflowId, jobId, parentJobId);
    if (deleted == 0) {
      throw new com.sunny.datapillar.common.exception.NotFoundException(
          "Dependency does not exist");
    }
    log.info(
        "Deleted dependency: workflowId={}, jobId={}, parentJobId={}",
        workflowId,
        jobId,
        parentJobId);
  }

  /** Verify that no loop will occur after adding dependencies */
  private void validateNoCycle(Long workflowId, Long jobId, Long parentJobId) {
    // Get all tasks under the workflow
    List<JobResponse> jobs = jobInfoMapper.selectJobsByWorkflowId(workflowId);
    // Get existing dependencies
    List<JobDependencyResponse> dependencies = dependencyMapper.selectByWorkflowId(workflowId);

    DagBuilder dagBuilder = new DagBuilder();

    // Add all nodes
    for (JobResponse job : jobs) {
      dagBuilder.addNode(job.getId());
    }

    // Add existing edge
    for (JobDependencyResponse dep : dependencies) {
      dagBuilder.addEdge(dep.getParentJobId(), dep.getJobId());
    }

    // Add new edge
    dagBuilder.addEdge(parentJobId, jobId);

    // Verify that there is no loop
    try {
      dagBuilder.validate();
    } catch (DagValidationException e) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "Workflow has circular dependencies");
    }
  }
}
