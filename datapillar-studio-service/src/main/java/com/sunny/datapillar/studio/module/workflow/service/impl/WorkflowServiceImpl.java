package com.sunny.datapillar.studio.module.workflow.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.studio.module.workflow.client.AirflowClient;
import com.sunny.datapillar.studio.module.workflow.dag.DagBuilder;
import com.sunny.datapillar.studio.module.workflow.dag.DagValidationException;
import com.sunny.datapillar.studio.module.workflow.dto.JobDependencyDto;
import com.sunny.datapillar.studio.module.workflow.dto.JobDto;
import com.sunny.datapillar.studio.module.workflow.dto.WorkflowDto;
import com.sunny.datapillar.studio.module.workflow.entity.JobWorkflow;
import com.sunny.datapillar.studio.module.workflow.mapper.JobDependencyMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobInfoMapper;
import com.sunny.datapillar.studio.module.workflow.mapper.JobWorkflowMapper;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowService;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流服务实现
 *
 * @author sunny
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final JobWorkflowMapper workflowMapper;
    private final JobInfoMapper jobInfoMapper;
    private final JobDependencyMapper dependencyMapper;
    private final AirflowClient airflowClient;

    // ==================== 工作流 CRUD ====================

    @Override
    public IPage<WorkflowDto.ListItem> getWorkflowPage(Page<WorkflowDto.ListItem> page, Long projectId, String workflowName, Integer status) {
        return workflowMapper.selectWorkflowPage(page, projectId, workflowName, status);
    }

    @Override
    public WorkflowDto.Response getWorkflowDetail(Long id) {
        WorkflowDto.Response workflow = workflowMapper.selectWorkflowDetail(id);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, id);
        }

        List<JobDto.Response> jobs = jobInfoMapper.selectJobsByWorkflowId(id);
        workflow.setJobs(jobs);

        List<JobDependencyDto.Response> dependencies = dependencyMapper.selectByWorkflowId(id);
        workflow.setDependencies(dependencies);

        return workflow;
    }

    @Override
    @Transactional
    public Long createWorkflow(WorkflowDto.Create dto) {
        JobWorkflow workflow = new JobWorkflow();
        BeanUtils.copyProperties(dto, workflow);
        workflow.setStatus(0);

        workflowMapper.insert(workflow);
        log.info("Created workflow: id={}, name={}", workflow.getId(), workflow.getWorkflowName());
        return workflow.getId();
    }

    @Override
    @Transactional
    public void updateWorkflow(Long id, WorkflowDto.Update dto) {
        JobWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, id);
        }

        if (dto.getWorkflowName() != null) {
            workflow.setWorkflowName(dto.getWorkflowName());
        }
        if (dto.getTriggerType() != null) {
            workflow.setTriggerType(dto.getTriggerType());
            // 手动触发(4)或API触发(5)时，清空 triggerValue
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
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, id);
        }

        // 如果已发布，先删除Airflow DAG
        if (workflow.getStatus() == 1 || workflow.getStatus() == 2) {
            String dagId = buildDagId(workflow);
            try {
                airflowClient.delete("/dags/" + dagId, JsonNode.class);
                log.info("Deleted Airflow DAG: {}", dagId);
            } catch (Exception e) {
                log.warn("Failed to delete Airflow DAG: {}, error: {}", dagId, e.getMessage());
            }
        }

        // 逻辑删除本地数据
        dependencyMapper.deleteByWorkflowId(id);
        jobInfoMapper.deleteByWorkflowId(id);
        workflowMapper.deleteById(id);

        log.info("Deleted workflow: id={}", id);
    }

    // ==================== DAG 管理 ====================

    @Override
    @Transactional
    public void publishWorkflow(Long id) {
        JobWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, id);
        }

        List<JobDto.Response> jobs = jobInfoMapper.selectJobsByWorkflowId(id);
        List<JobDependencyDto.Response> dependencies = dependencyMapper.selectByWorkflowId(id);

        validateDag(jobs, dependencies);

        Map<String, Object> request = buildAirflowDeployRequest(workflow, jobs, dependencies);

        JsonNode response = airflowClient.post("/dags", request, JsonNode.class);
        log.info("Published workflow to Airflow: id={}, response={}", id, response);

        // 强制刷新该 DAG
        String dagId = buildDagId(workflow);
        airflowClient.post("/dags/" + dagId + "/reserialize", Map.of(), JsonNode.class);

        workflow.setStatus(1);
        workflowMapper.updateById(workflow);
    }

    @Override
    @Transactional
    public void pauseWorkflow(Long id) {
        JobWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, id);
        }

        if (workflow.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_INVALID_STATUS, "只有已发布的工作流才能暂停");
        }

        String dagId = buildDagId(workflow);
        airflowClient.patch("/dags/" + dagId, Map.of("is_paused", true), JsonNode.class);

        workflow.setStatus(2);
        workflowMapper.updateById(workflow);
        log.info("Paused workflow: id={}", id);
    }

    @Override
    @Transactional
    public void resumeWorkflow(Long id) {
        JobWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, id);
        }

        if (workflow.getStatus() != 2) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_INVALID_STATUS, "只有已暂停的工作流才能恢复");
        }

        String dagId = buildDagId(workflow);
        airflowClient.patch("/dags/" + dagId, Map.of("is_paused", false), JsonNode.class);

        workflow.setStatus(1);
        workflowMapper.updateById(workflow);
        log.info("Resumed workflow: id={}", id);
    }

    @Override
    public JsonNode getDagDetail(Long id) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);
        return airflowClient.get("/dags/" + dagId, JsonNode.class);
    }

    @Override
    public JsonNode getDagVersions(Long id, int limit, int offset) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);
        return airflowClient.get("/dags/" + dagId + "/versions?limit=" + limit + "&offset=" + offset, JsonNode.class);
    }

    @Override
    public JsonNode getDagVersion(Long id, int versionNumber) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);
        return airflowClient.get("/dags/" + dagId + "/versions/" + versionNumber, JsonNode.class);
    }

    // ==================== DAG Run 管理 ====================

    @Override
    public JsonNode triggerWorkflow(Long id, WorkflowDto.TriggerRequest request) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);

        Map<String, Object> body = new HashMap<>();
        if (request != null) {
            if (request.getLogicalDate() != null) {
                body.put("logical_date", request.getLogicalDate());
            }
            if (request.getConf() != null) {
                body.put("conf", request.getConf());
            }
        }

        return airflowClient.post("/dags/" + dagId + "/runs", body, JsonNode.class);
    }

    @Override
    public JsonNode getWorkflowRuns(Long id, int limit, int offset, String state) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);

        StringBuilder url = new StringBuilder("/dags/" + dagId + "/runs?limit=" + limit + "&offset=" + offset);
        if (state != null && !state.isEmpty()) {
            url.append("&state=").append(state);
        }

        return airflowClient.get(url.toString(), JsonNode.class);
    }

    @Override
    public JsonNode getWorkflowRun(Long id, String runId) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);
        return airflowClient.get("/dags/" + dagId + "/runs/" + runId, JsonNode.class);
    }

    // ==================== Job 管理 ====================

    @Override
    public JsonNode getRunJobs(Long id, String runId) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);
        return airflowClient.get("/dags/" + dagId + "/runs/" + runId + "/tasks", JsonNode.class);
    }

    @Override
    public JsonNode getRunJob(Long id, String runId, String jobId) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);
        return airflowClient.get("/dags/" + dagId + "/runs/" + runId + "/tasks/" + jobId, JsonNode.class);
    }

    @Override
    public JsonNode getJobLogs(Long id, String runId, String jobId, int tryNumber) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);
        return airflowClient.get("/dags/" + dagId + "/runs/" + runId + "/tasks/" + jobId + "/logs?try_number=" + tryNumber, JsonNode.class);
    }

    @Override
    public JsonNode rerunJob(Long id, String runId, String jobId, WorkflowDto.RerunJobRequest request) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);

        Map<String, Object> body = new HashMap<>();
        if (request != null) {
            body.put("downstream", request.isDownstream());
            body.put("upstream", request.isUpstream());
        }

        return airflowClient.post("/dags/" + dagId + "/runs/" + runId + "/tasks/" + jobId + "/rerun", body, JsonNode.class);
    }

    @Override
    public JsonNode setJobState(Long id, String runId, String jobId, WorkflowDto.SetJobStateRequest request) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);

        Map<String, Object> body = new HashMap<>();
        body.put("new_state", request.getNewState());
        body.put("include_upstream", request.isIncludeUpstream());
        body.put("include_downstream", request.isIncludeDownstream());

        return airflowClient.patch("/dags/" + dagId + "/runs/" + runId + "/tasks/" + jobId + "/state", body, JsonNode.class);
    }

    @Override
    public JsonNode clearJobs(Long id, String runId, WorkflowDto.ClearJobsRequest request) {
        JobWorkflow workflow = getWorkflowById(id);
        String dagId = buildDagId(workflow);

        Map<String, Object> body = new HashMap<>();
        body.put("task_ids", request.getJobIds());
        body.put("only_failed", request.isOnlyFailed());
        body.put("reset_dag_runs", request.isResetDagRuns());
        body.put("include_upstream", request.isIncludeUpstream());
        body.put("include_downstream", request.isIncludeDownstream());

        return airflowClient.post("/dags/" + dagId + "/runs/" + runId + "/clear", body, JsonNode.class);
    }

    // ==================== 私有方法 ====================

    private JobWorkflow getWorkflowById(Long id) {
        JobWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ErrorCode.ADMIN_WORKFLOW_NOT_FOUND, id);
        }
        return workflow;
    }

    /**
     * 构建DAG ID，格式: datapillar_project_{projectId}_workflow_{workflowId}
     */
    private String buildDagId(JobWorkflow workflow) {
        return "datapillar_project_" + workflow.getProjectId() + "_workflow_" + workflow.getId();
    }

    private void validateDag(List<JobDto.Response> jobs, List<JobDependencyDto.Response> dependencies) {
        DagBuilder dagBuilder = new DagBuilder();

        for (JobDto.Response job : jobs) {
            dagBuilder.addNode(job.getId());
        }

        for (JobDependencyDto.Response dep : dependencies) {
            dagBuilder.addEdge(dep.getParentJobId(), dep.getJobId());
        }

        try {
            dagBuilder.validate();
        } catch (DagValidationException e) {
            throw new BusinessException(ErrorCode.ADMIN_DAG_HAS_CYCLE);
        }
    }

    private Map<String, Object> buildAirflowDeployRequest(JobWorkflow workflow,
            List<JobDto.Response> jobs, List<JobDependencyDto.Response> dependencies) {

        List<Map<String, Object>> jobList = jobs.stream().map(job -> Map.<String, Object>of(
                "id", job.getId(),
                "job_name", "job_" + job.getId(),
                "job_type", job.getJobTypeCode(),
                "job_params", job.getJobParams() != null ? job.getJobParams() : Map.of(),
                "timeout_seconds", job.getTimeoutSeconds() != null ? job.getTimeoutSeconds() : 0,
                "max_retry_times", job.getMaxRetryTimes() != null ? job.getMaxRetryTimes() : 0
        )).collect(Collectors.toList());

        List<Map<String, Object>> depList = dependencies.stream().map(dep -> Map.<String, Object>of(
                "job_id", dep.getJobId(),
                "parent_job_id", dep.getParentJobId()
        )).collect(Collectors.toList());

        Map<String, Object> workflowMap = Map.of(
                "workflow_name", buildDagId(workflow),
                "trigger_type", workflow.getTriggerType(),
                "trigger_value", workflow.getTriggerValue() != null ? workflow.getTriggerValue() : "",
                "timeout_seconds", workflow.getTimeoutSeconds() != null ? workflow.getTimeoutSeconds() : 0,
                "max_retry_times", workflow.getMaxRetryTimes() != null ? workflow.getMaxRetryTimes() : 0,
                "jobs", jobList,
                "dependencies", depList
        );

        // namespace格式: project_{projectId}
        String namespace = "project_" + workflow.getProjectId();

        return Map.of(
                "namespace", namespace,
                "workflow", workflowMap
        );
    }
}
