package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.common.ParamValidator;
import com.sunny.job.server.dto.Dependency;
import com.sunny.job.server.dto.Job;
import com.sunny.job.server.dto.Layout;
import com.sunny.job.server.dto.Workflow;
import com.sunny.job.server.entity.JobWorkflow;
import com.sunny.job.server.service.JobWorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/workflow")
public class JobWorkflowController {

    private final JobWorkflowService workflowService;

    public JobWorkflowController(JobWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    // ==================== 工作流操作 ====================

    /**
     * 查询工作流列表
     */
    @GetMapping("/list")
    public ApiResponse<List<Workflow>> list(@RequestParam Long namespaceId) {
        List<JobWorkflow> list = workflowService.list(
                new LambdaQueryWrapper<JobWorkflow>()
                        .eq(JobWorkflow::getNamespaceId, namespaceId)
                        .orderByDesc(JobWorkflow::getId)
        );
        List<Workflow> voList = list.stream()
                .map(Workflow::from)
                .collect(Collectors.toList());
        return ApiResponse.success(voList);
    }

    /**
     * 获取工作流详情（含任务和依赖）
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getGraph(@PathVariable Long id) {
        JobWorkflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return ApiResponse.error(404, "工作流不存在");
        }
        List<Job> jobs = workflowService.getJobs(id);
        List<Dependency> dependencies = workflowService.getDependencies(id);

        Map<String, Object> result = new HashMap<>();
        result.put("workflow", Workflow.from(workflow));
        result.put("jobs", jobs);
        result.put("dependencies", dependencies);
        return ApiResponse.success(result);
    }

    /**
     * 创建工作流
     */
    @PostMapping
    public ApiResponse<Long> create(@RequestParam Long namespaceId,
                                    @RequestParam String workflowName,
                                    @RequestParam(required = false) Integer triggerType,
                                    @RequestParam(required = false) String triggerValue,
                                    @RequestParam(required = false) String description) {
        ParamValidator.requireNotNull(namespaceId, "namespaceId");
        ParamValidator.requireNotBlank(workflowName, "workflowName");

        Long workflowId = workflowService.createWorkflow(namespaceId, workflowName,
                triggerType, triggerValue, description);
        return ApiResponse.success(workflowId);
    }

    /**
     * 更新工作流基本信息
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @RequestParam(required = false) String workflowName,
                                    @RequestParam(required = false) Integer triggerType,
                                    @RequestParam(required = false) String triggerValue,
                                    @RequestParam(required = false) Integer timeoutSeconds,
                                    @RequestParam(required = false) Integer maxRetryTimes,
                                    @RequestParam(required = false) Integer priority,
                                    @RequestParam(required = false) String description) {
        workflowService.updateWorkflow(id, workflowName, triggerType, triggerValue,
                timeoutSeconds, maxRetryTimes, priority, description);
        return ApiResponse.success();
    }

    /**
     * 删除工作流
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workflowService.deleteWorkflow(id);
        return ApiResponse.success();
    }

    // ==================== 任务节点操作 ====================

    /**
     * 添加任务
     */
    @PostMapping("/{workflowId}/job")
    public ApiResponse<Long> addJob(@PathVariable Long workflowId,
                                    @RequestBody Job job) {
        ParamValidator.requireNotBlank(job.getJobName(), "jobName");
        Long jobId = workflowService.addJob(workflowId, job);
        return ApiResponse.success(jobId);
    }

    /**
     * 更新任务（不含位置）
     */
    @PutMapping("/{workflowId}/job/{jobId}")
    public ApiResponse<Void> updateJob(@PathVariable Long workflowId,
                                       @PathVariable Long jobId,
                                       @RequestBody Job job) {
        workflowService.updateJob(workflowId, jobId, job);
        return ApiResponse.success();
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{workflowId}/job/{jobId}")
    public ApiResponse<Void> deleteJob(@PathVariable Long workflowId,
                                       @PathVariable Long jobId) {
        workflowService.deleteJob(workflowId, jobId);
        return ApiResponse.success();
    }

    // ==================== 依赖关系操作 ====================

    /**
     * 添加依赖
     */
    @PostMapping("/{workflowId}/dependency")
    public ApiResponse<Long> addDependency(@PathVariable Long workflowId,
                                           @RequestParam Long jobId,
                                           @RequestParam Long parentJobId) {
        Long depId = workflowService.addDependency(workflowId, jobId, parentJobId);
        return ApiResponse.success(depId);
    }

    /**
     * 删除依赖
     */
    @DeleteMapping("/{workflowId}/dependency")
    public ApiResponse<Void> deleteDependency(@PathVariable Long workflowId,
                                              @RequestParam Long jobId,
                                              @RequestParam Long parentJobId) {
        workflowService.deleteDependency(workflowId, jobId, parentJobId);
        return ApiResponse.success();
    }

    // ==================== 布局操作 ====================

    /**
     * 批量保存节点位置
     */
    @PostMapping("/{workflowId}/layout")
    public ApiResponse<Void> saveLayout(@PathVariable Long workflowId,
                                        @RequestBody Layout layout) {
        Map<Long, double[]> positions = new HashMap<>();
        if (layout.getPositions() != null) {
            for (Layout.Position pos : layout.getPositions()) {
                positions.put(pos.getJobId(), new double[]{pos.getX(), pos.getY()});
            }
        }
        workflowService.saveLayout(workflowId, positions);
        return ApiResponse.success();
    }

    // ==================== 工作流状态操作 ====================

    /**
     * 上线工作流
     */
    @PostMapping("/{id}/online")
    public ApiResponse<Void> online(@PathVariable Long id) {
        workflowService.online(id);
        return ApiResponse.success();
    }

    /**
     * 下线工作流
     */
    @PostMapping("/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable Long id) {
        workflowService.offline(id);
        return ApiResponse.success();
    }

    /**
     * 手动触发工作流
     */
    @PostMapping("/{id}/trigger")
    public ApiResponse<Void> trigger(@PathVariable Long id) {
        workflowService.trigger(id);
        return ApiResponse.success();
    }
}
