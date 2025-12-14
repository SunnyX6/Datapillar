package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.entity.JobDependency;
import com.sunny.job.server.entity.JobWorkflow;
import com.sunny.job.server.service.JobDependencyService;
import com.sunny.job.server.service.JobWorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务依赖 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/dependency")
public class JobDependencyController {

    private final JobDependencyService dependencyService;
    private final JobWorkflowService workflowService;

    public JobDependencyController(JobDependencyService dependencyService,
                                    JobWorkflowService workflowService) {
        this.dependencyService = dependencyService;
        this.workflowService = workflowService;
    }

    /**
     * 查询依赖列表（按工作流）
     */
    @GetMapping("/list")
    public ApiResponse<List<JobDependency>> list(@RequestParam Long workflowId) {
        List<JobDependency> list = dependencyService.list(
                new LambdaQueryWrapper<JobDependency>()
                        .eq(JobDependency::getWorkflowId, workflowId)
        );
        return ApiResponse.success(list);
    }

    /**
     * 创建依赖
     */
    @PostMapping
    public ApiResponse<JobDependency> create(@RequestBody JobDependency dependency) {
        // 检查工作流是否存在且未上线
        JobWorkflow workflow = workflowService.getById(dependency.getWorkflowId());
        if (workflow == null) {
            return ApiResponse.error(400, "工作流不存在");
        }
        if (workflow.isOnline()) {
            return ApiResponse.error(400, "上线状态的工作流不允许修改依赖，请先下线");
        }

        // 检查是否已存在相同依赖
        long count = dependencyService.count(
                new LambdaQueryWrapper<JobDependency>()
                        .eq(JobDependency::getWorkflowId, dependency.getWorkflowId())
                        .eq(JobDependency::getJobId, dependency.getJobId())
                        .eq(JobDependency::getParentJobId, dependency.getParentJobId())
        );
        if (count > 0) {
            return ApiResponse.error(400, "依赖关系已存在");
        }

        // 检查自依赖
        if (dependency.getJobId().equals(dependency.getParentJobId())) {
            return ApiResponse.error(400, "任务不能依赖自身");
        }

        // DAG 环检测
        try {
            dependencyService.validateSingleDependency(dependency);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }

        dependencyService.save(dependency);
        return ApiResponse.success(dependency);
    }

    /**
     * 删除依赖
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        JobDependency existing = dependencyService.getById(id);
        if (existing == null) {
            return ApiResponse.error(404, "依赖关系不存在");
        }

        JobWorkflow workflow = workflowService.getById(existing.getWorkflowId());
        if (workflow != null && workflow.isOnline()) {
            return ApiResponse.error(400, "上线状态的工作流不允许删除依赖，请先下线");
        }

        dependencyService.removeById(id);
        return ApiResponse.success();
    }

    /**
     * 批量保存依赖（覆盖式）
     */
    @PostMapping("/batch")
    public ApiResponse<Void> batchSave(@RequestParam Long workflowId,
                                        @RequestBody List<JobDependency> dependencies) {
        // 检查工作流状态
        JobWorkflow workflow = workflowService.getById(workflowId);
        if (workflow == null) {
            return ApiResponse.error(400, "工作流不存在");
        }
        if (workflow.isOnline()) {
            return ApiResponse.error(400, "上线状态的工作流不允许修改依赖，请先下线");
        }

        // DAG 环检测（在删除旧数据前先验证）
        if (dependencies != null && !dependencies.isEmpty()) {
            for (JobDependency dep : dependencies) {
                dep.setWorkflowId(workflowId);
                // 检查自依赖
                if (dep.getJobId().equals(dep.getParentJobId())) {
                    return ApiResponse.error(400, "任务不能依赖自身: jobId=" + dep.getJobId());
                }
            }
            try {
                dependencyService.validateNoCycle(workflowId, dependencies);
            } catch (IllegalArgumentException e) {
                return ApiResponse.error(400, e.getMessage());
            }
        }

        // 删除原有依赖
        dependencyService.remove(
                new LambdaQueryWrapper<JobDependency>()
                        .eq(JobDependency::getWorkflowId, workflowId)
        );

        // 批量插入新依赖
        if (dependencies != null && !dependencies.isEmpty()) {
            dependencyService.saveBatch(dependencies);
        }

        return ApiResponse.success();
    }
}
