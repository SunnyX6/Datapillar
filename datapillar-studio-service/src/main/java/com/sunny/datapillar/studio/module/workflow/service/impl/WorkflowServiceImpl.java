package com.sunny.datapillar.studio.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.connector.airflow.AirflowConnector;
import com.sunny.datapillar.connector.runtime.ConnectorKernel;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.IdempotencyDescriptor;
import com.sunny.datapillar.studio.context.TenantContextHolder;
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
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;
import com.sunny.datapillar.studio.module.workflow.mapper.JobDependencyMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobInfoMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobWorkflowMapper;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowService;
import com.sunny.datapillar.studio.module.workflow.service.dag.DagBuilder;
import com.sunny.datapillar.studio.module.workflow.service.dag.DagValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Workflow service implementation. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

  private static final String IDEMPOTENCY_STEP_DEPLOY = "AIRFLOW_DEPLOY";
  private static final String IDEMPOTENCY_STEP_DELETE = "AIRFLOW_DELETE";
  private static final String IDEMPOTENCY_STEP_PAUSE = "AIRFLOW_PAUSE";
  private static final String IDEMPOTENCY_STEP_RESUME = "AIRFLOW_RESUME";
  private static final String IDEMPOTENCY_STEP_TRIGGER = "AIRFLOW_TRIGGER";
  private static final String IDEMPOTENCY_STEP_RERUN = "AIRFLOW_RERUN_TASK";
  private static final String IDEMPOTENCY_STEP_SET_STATE = "AIRFLOW_SET_TASK_STATE";
  private static final String IDEMPOTENCY_STEP_CLEAR = "AIRFLOW_CLEAR_TASKS";

  private final JobWorkflowMapper workflowMapper;
  private final JobInfoMapper jobInfoMapper;
  private final JobDependencyMapper dependencyMapper;
  private final ConnectorKernel connectorKernel;
  private final ObjectMapper objectMapper;

  @Override
  public IPage<WorkflowListItemResponse> getWorkflowPage(
      Page<WorkflowListItemResponse> page, Long projectId, String workflowName, Integer status) {
    return workflowMapper.selectWorkflowPage(page, projectId, workflowName, status);
  }

  @Override
  public WorkflowResponse getWorkflowDetail(Long id) {
    WorkflowResponse workflow = workflowMapper.selectWorkflowDetail(id);
    if (workflow == null) {
      throw new NotFoundException("Workflow does not exist: workflowId=%s", id);
    }

    List<JobResponse> jobs = jobInfoMapper.selectJobsByWorkflowId(id);
    workflow.setJobs(jobs);

    List<JobDependencyResponse> dependencies = dependencyMapper.selectByWorkflowId(id);
    workflow.setDependencies(dependencies);

    return workflow;
  }

  @Override
  @Transactional
  public Long createWorkflow(WorkflowCreateRequest dto) {
    JobWorkflow workflow = new JobWorkflow();
    BeanUtils.copyProperties(dto, workflow);
    workflow.setStatus(0);

    workflowMapper.insert(workflow);
    log.info("Created workflow: id={}, name={}", workflow.getId(), workflow.getWorkflowName());
    return workflow.getId();
  }

  @Override
  @Transactional
  public void updateWorkflow(Long id, WorkflowUpdateRequest dto) {
    JobWorkflow workflow = workflowMapper.selectById(id);
    if (workflow == null) {
      throw new NotFoundException("Workflow does not exist: workflowId=%s", id);
    }

    if (dto.getWorkflowName() != null) {
      workflow.setWorkflowName(dto.getWorkflowName());
    }
    if (dto.getTriggerType() != null) {
      workflow.setTriggerType(dto.getTriggerType());
      if (dto.getTriggerType() == 4 || dto.getTriggerType() == 5) {
        workflow.setTriggerValue(null);
      }
    }
    if (dto.getTriggerValue() != null) {
      workflow.setTriggerValue(dto.getTriggerValue());
    }
    if (dto.getTimeoutSeconds() != null) {
      workflow.setTimeoutSeconds(dto.getTimeoutSeconds());
    }
    if (dto.getMaxRetryTimes() != null) {
      workflow.setMaxRetryTimes(dto.getMaxRetryTimes());
    }
    if (dto.getPriority() != null) {
      workflow.setPriority(dto.getPriority());
    }
    if (dto.getDescription() != null) {
      workflow.setDescription(dto.getDescription());
    }

    workflowMapper.updateById(workflow);
    log.info("Updated workflow: id={}", id);
  }

  @Override
  @Transactional
  public void deleteWorkflow(Long id) {
    JobWorkflow workflow = workflowMapper.selectById(id);
    if (workflow == null) {
      throw new NotFoundException("Workflow does not exist: workflowId=%s", id);
    }

    if (workflow.getStatus() == 1 || workflow.getStatus() == 2) {
      JsonNode payload = objectMapper.createObjectNode().put("workflowId", id);
      invokeAirflow(
          AirflowConnector.OP_DELETE,
          payload,
          buildWorkflowIdempotency(id, IDEMPOTENCY_STEP_DELETE));
      log.info("Deleted airflow workflow: id={}", id);
    }

    dependencyMapper.deleteByWorkflowId(id);
    jobInfoMapper.deleteByWorkflowId(id);
    workflowMapper.deleteById(id);

    log.info("Deleted workflow: id={}", id);
  }

  @Override
  @Transactional
  public void publishWorkflow(Long id) {
    JobWorkflow workflow = workflowMapper.selectById(id);
    if (workflow == null) {
      throw new NotFoundException("Workflow does not exist: workflowId=%s", id);
    }

    List<JobResponse> jobs = jobInfoMapper.selectJobsByWorkflowId(id);
    List<JobDependencyResponse> dependencies = dependencyMapper.selectByWorkflowId(id);
    validateDag(jobs, dependencies);

    JsonNode payload = buildAirflowDeployPayload(workflow, jobs, dependencies);
    JsonNode response =
        invokeAirflow(
            AirflowConnector.OP_DEPLOY,
            payload,
            buildWorkflowIdempotency(id, IDEMPOTENCY_STEP_DEPLOY));
    log.info("Published workflow to airflow: id={}, response={}", id, response);

    workflow.setStatus(1);
    workflowMapper.updateById(workflow);
  }

  @Override
  @Transactional
  public void pauseWorkflow(Long id) {
    JobWorkflow workflow = workflowMapper.selectById(id);
    if (workflow == null) {
      throw new NotFoundException("Workflow does not exist: workflowId=%s", id);
    }
    if (workflow.getStatus() != 1) {
      throw new BadRequestException(
          "Workflow status is incorrect: %s", "Only published workflows can be paused");
    }

    JsonNode payload = objectMapper.createObjectNode().put("workflowId", id);
    invokeAirflow(
        AirflowConnector.OP_PAUSE, payload, buildWorkflowIdempotency(id, IDEMPOTENCY_STEP_PAUSE));

    workflow.setStatus(2);
    workflowMapper.updateById(workflow);
    log.info("Paused workflow: id={}", id);
  }

  @Override
  @Transactional
  public void resumeWorkflow(Long id) {
    JobWorkflow workflow = workflowMapper.selectById(id);
    if (workflow == null) {
      throw new NotFoundException("Workflow does not exist: workflowId=%s", id);
    }
    if (workflow.getStatus() != 2) {
      throw new BadRequestException(
          "Workflow status is incorrect: %s", "Only paused workflows can be resumed");
    }

    JsonNode payload = objectMapper.createObjectNode().put("workflowId", id);
    invokeAirflow(
        AirflowConnector.OP_RESUME, payload, buildWorkflowIdempotency(id, IDEMPOTENCY_STEP_RESUME));

    workflow.setStatus(1);
    workflowMapper.updateById(workflow);
    log.info("Resumed workflow: id={}", id);
  }

  @Override
  public JsonNode getDagDetail(Long id) {
    getWorkflowById(id);
    JsonNode payload = objectMapper.createObjectNode().put("workflowId", id);
    return invokeAirflow(AirflowConnector.OP_GET_DAG, payload, null);
  }

  @Override
  public JsonNode getDagVersions(Long id, int limit, int offset) {
    getWorkflowById(id);
    JsonNode payload =
        objectMapper
            .createObjectNode()
            .put("workflowId", id)
            .put("limit", limit)
            .put("offset", offset);
    return invokeAirflow(AirflowConnector.OP_LIST_DAG_VERSIONS, payload, null);
  }

  @Override
  public JsonNode getDagVersion(Long id, int versionNumber) {
    getWorkflowById(id);
    JsonNode payload =
        objectMapper.createObjectNode().put("workflowId", id).put("versionNumber", versionNumber);
    return invokeAirflow(AirflowConnector.OP_GET_DAG_VERSION, payload, null);
  }

  @Override
  public JsonNode triggerWorkflow(Long id, WorkflowTriggerRequest request) {
    getWorkflowById(id);

    var body = objectMapper.createObjectNode();
    if (request != null) {
      if (StringUtils.hasText(request.getLogicalDate())) {
        body.put("logical_date", request.getLogicalDate());
      }
      if (request.getConf() != null) {
        body.set("conf", objectMapper.valueToTree(request.getConf()));
      }
    }

    JsonNode payload = objectMapper.createObjectNode().put("workflowId", id).set("body", body);
    return invokeAirflow(
        AirflowConnector.OP_TRIGGER_RUN,
        payload,
        buildWorkflowIdempotency(id, IDEMPOTENCY_STEP_TRIGGER));
  }

  @Override
  public JsonNode getWorkflowRuns(Long id, int limit, int offset, String state) {
    getWorkflowById(id);
    var payload =
        objectMapper
            .createObjectNode()
            .put("workflowId", id)
            .put("limit", limit)
            .put("offset", offset);
    if (StringUtils.hasText(state)) {
      payload.put("state", state);
    }
    return invokeAirflow(AirflowConnector.OP_LIST_RUNS, payload, null);
  }

  @Override
  public JsonNode getWorkflowRun(Long id, String runId) {
    getWorkflowById(id);
    JsonNode payload = objectMapper.createObjectNode().put("workflowId", id).put("runId", runId);
    return invokeAirflow(AirflowConnector.OP_GET_RUN, payload, null);
  }

  @Override
  public JsonNode getRunJobs(Long id, String runId) {
    getWorkflowById(id);
    JsonNode payload = objectMapper.createObjectNode().put("workflowId", id).put("runId", runId);
    return invokeAirflow(AirflowConnector.OP_LIST_TASKS, payload, null);
  }

  @Override
  public JsonNode getRunJob(Long id, String runId, String jobId) {
    getWorkflowById(id);
    JsonNode payload =
        objectMapper
            .createObjectNode()
            .put("workflowId", id)
            .put("runId", runId)
            .put("taskId", jobId);
    return invokeAirflow(AirflowConnector.OP_GET_TASK, payload, null);
  }

  @Override
  public JsonNode getJobLogs(Long id, String runId, String jobId, int tryNumber) {
    getWorkflowById(id);
    JsonNode payload =
        objectMapper
            .createObjectNode()
            .put("workflowId", id)
            .put("runId", runId)
            .put("taskId", jobId)
            .put("tryNumber", tryNumber);
    return invokeAirflow(AirflowConnector.OP_GET_TASK_LOGS, payload, null);
  }

  @Override
  public JsonNode rerunJob(Long id, String runId, String jobId, WorkflowRerunJobRequest request) {
    getWorkflowById(id);

    var body = objectMapper.createObjectNode();
    if (request != null) {
      body.put("downstream", request.isDownstream());
      body.put("upstream", request.isUpstream());
    }

    JsonNode payload =
        objectMapper
            .createObjectNode()
            .put("workflowId", id)
            .put("runId", runId)
            .put("taskId", jobId)
            .set("body", body);
    return invokeAirflow(
        AirflowConnector.OP_RERUN_TASK,
        payload,
        buildWorkflowIdempotency(id, IDEMPOTENCY_STEP_RERUN + ":" + runId + ":" + jobId));
  }

  @Override
  public JsonNode setJobState(
      Long id, String runId, String jobId, WorkflowSetJobStatusRequest request) {
    getWorkflowById(id);

    var body = objectMapper.createObjectNode();
    body.put("new_state", request.getNewState());
    body.put("include_upstream", request.isIncludeUpstream());
    body.put("include_downstream", request.isIncludeDownstream());

    JsonNode payload =
        objectMapper
            .createObjectNode()
            .put("workflowId", id)
            .put("runId", runId)
            .put("taskId", jobId)
            .set("body", body);
    return invokeAirflow(
        AirflowConnector.OP_SET_TASK_STATE,
        payload,
        buildWorkflowIdempotency(id, IDEMPOTENCY_STEP_SET_STATE + ":" + runId + ":" + jobId));
  }

  @Override
  public JsonNode clearJobs(Long id, String runId, WorkflowClearJobsRequest request) {
    getWorkflowById(id);

    var body = objectMapper.createObjectNode();
    body.set("task_ids", objectMapper.valueToTree(request.getJobIds()));
    body.put("only_failed", request.isOnlyFailed());
    body.put("reset_dag_runs", request.isResetDagRuns());
    body.put("include_upstream", request.isIncludeUpstream());
    body.put("include_downstream", request.isIncludeDownstream());

    JsonNode payload =
        objectMapper.createObjectNode().put("workflowId", id).put("runId", runId).set("body", body);
    return invokeAirflow(
        AirflowConnector.OP_CLEAR_TASKS,
        payload,
        buildWorkflowIdempotency(id, IDEMPOTENCY_STEP_CLEAR + ":" + runId));
  }

  private JobWorkflow getWorkflowById(Long id) {
    JobWorkflow workflow = workflowMapper.selectById(id);
    if (workflow == null) {
      throw new NotFoundException("Workflow does not exist: workflowId=%s", id);
    }
    return workflow;
  }

  private void validateDag(List<JobResponse> jobs, List<JobDependencyResponse> dependencies) {
    DagBuilder dagBuilder = new DagBuilder();

    for (JobResponse job : jobs) {
      dagBuilder.addNode(job.getId());
    }

    for (JobDependencyResponse dependency : dependencies) {
      dagBuilder.addEdge(dependency.getParentJobId(), dependency.getJobId());
    }

    try {
      dagBuilder.validate();
    } catch (DagValidationException ignored) {
      throw new BadRequestException("Workflow has circular dependencies");
    }
  }

  private JsonNode buildAirflowDeployPayload(
      JobWorkflow workflow, List<JobResponse> jobs, List<JobDependencyResponse> dependencies) {

    List<Map<String, Object>> jobList =
        jobs.stream()
            .map(
                job ->
                    Map.<String, Object>of(
                        "id",
                        job.getId(),
                        "job_name",
                        "job_" + job.getId(),
                        "job_type",
                        job.getJobTypeCode(),
                        "job_params",
                        job.getJobParams() != null ? job.getJobParams() : Map.of(),
                        "timeout_seconds",
                        job.getTimeoutSeconds() != null ? job.getTimeoutSeconds() : 0,
                        "max_retry_times",
                        job.getMaxRetryTimes() != null ? job.getMaxRetryTimes() : 0))
            .collect(Collectors.toList());

    List<Map<String, Object>> dependencyList =
        dependencies.stream()
            .map(
                dep ->
                    Map.<String, Object>of(
                        "job_id", dep.getJobId(),
                        "parent_job_id", dep.getParentJobId()))
            .collect(Collectors.toList());

    Map<String, Object> workflowMap =
        Map.of(
            "workflow_name",
            workflow.getWorkflowName(),
            "trigger_type",
            workflow.getTriggerType(),
            "trigger_value",
            workflow.getTriggerValue() != null ? workflow.getTriggerValue() : "",
            "timeout_seconds",
            workflow.getTimeoutSeconds() != null ? workflow.getTimeoutSeconds() : 0,
            "max_retry_times",
            workflow.getMaxRetryTimes() != null ? workflow.getMaxRetryTimes() : 0,
            "jobs",
            jobList,
            "dependencies",
            dependencyList);

    return objectMapper
        .createObjectNode()
        .put("workflowId", workflow.getId())
        .set("workflow", objectMapper.valueToTree(workflowMap));
  }

  private JsonNode invokeAirflow(
      String operationId, JsonNode payload, IdempotencyDescriptor idempotencyDescriptor) {
    ConnectorInvocation.Builder builder =
        ConnectorInvocation.builder(AirflowConnector.CONNECTOR_ID, operationId).payload(payload);
    if (idempotencyDescriptor != null) {
      builder.idempotency(idempotencyDescriptor);
    }
    return connectorKernel.invoke(builder.build()).payload();
  }

  private IdempotencyDescriptor buildWorkflowIdempotency(Long workflowId, String action) {
    String tenantCode = requiredTenantCode();
    String normalizedAction = action == null ? "UNKNOWN" : action.trim().toUpperCase(Locale.ROOT);
    String key = "airflow:%s:%d:%s".formatted(tenantCode, workflowId, normalizedAction);
    return IdempotencyDescriptor.of(key, normalizedAction);
  }

  private String requiredTenantCode() {
    String tenantCode = TenantContextHolder.getTenantCode();
    if (!StringUtils.hasText(tenantCode)) {
      throw new UnauthorizedException("Unauthorized access");
    }
    return tenantCode.trim().toLowerCase(Locale.ROOT);
  }
}
