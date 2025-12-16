package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.common.ParamValidator;
import com.sunny.job.server.entity.JobWorkflow;
import com.sunny.job.server.entity.JobWorkflowDependency;
import com.sunny.job.server.service.JobWorkflowDependencyService;
import com.sunny.job.server.service.JobWorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 跨工作流依赖 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/workflow-dependency")
public class JobWorkflowDependencyController {

    private final JobWorkflowDependencyService dependencyService;
    private final JobWorkflowService workflowService;

    public JobWorkflowDependencyController(JobWorkflowDependencyService dependencyService,
                                            JobWorkflowService workflowService) {
        this.dependencyService = dependencyService;
        this.workflowService = workflowService;
    }

    /**
     * 查询跨工作流依赖列表
     */
    @GetMapping("/list")
    public ApiResponse<List<JobWorkflowDependency>> list(@RequestParam Long workflowId) {
        List<JobWorkflowDependency> list = dependencyService.list(
                new LambdaQueryWrapper<JobWorkflowDependency>()
                        .eq(JobWorkflowDependency::getWorkflowId, workflowId)
        );
        return ApiResponse.success(list);
    }

    /**
     * 创建跨工作流依赖
     */
    @PostMapping
    public ApiResponse<JobWorkflowDependency> create(@RequestBody JobWorkflowDependency dependency) {
        // 参数校验
        ParamValidator.requireNotNull(dependency.getWorkflowId(), "workflowId");
        ParamValidator.requireNotNull(dependency.getDependWorkflowId(), "dependWorkflowId");
        ParamValidator.requireNotNull(dependency.getDependJobId(), "dependJobId");

        // 检查当前工作流是否存在
        JobWorkflow workflow = workflowService.getById(dependency.getWorkflowId());
        if (workflow == null) {
            return ApiResponse.error(400, "工作流不存在");
        }

        // 检查依赖的工作流是否存在
        JobWorkflow dependWorkflow = workflowService.getById(dependency.getDependWorkflowId());
        if (dependWorkflow == null) {
            return ApiResponse.error(400, "依赖的工作流不存在");
        }

        // 检查自依赖
        if (dependency.getWorkflowId().equals(dependency.getDependWorkflowId())) {
            return ApiResponse.error(400, "工作流不能依赖自身");
        }

        // 检查是否已存在相同依赖
        long count = dependencyService.count(
                new LambdaQueryWrapper<JobWorkflowDependency>()
                        .eq(JobWorkflowDependency::getWorkflowId, dependency.getWorkflowId())
                        .eq(JobWorkflowDependency::getDependWorkflowId, dependency.getDependWorkflowId())
                        .eq(JobWorkflowDependency::getDependJobId, dependency.getDependJobId())
        );
        if (count > 0) {
            return ApiResponse.error(400, "跨工作流依赖关系已存在");
        }

        dependencyService.save(dependency);
        return ApiResponse.success(dependency);
    }

    /**
     * 删除跨工作流依赖
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        JobWorkflowDependency existing = dependencyService.getById(id);
        if (existing == null) {
            return ApiResponse.error(404, "跨工作流依赖不存在");
        }

        dependencyService.removeById(id);
        return ApiResponse.success();
    }

    /**
     * 批量保存跨工作流依赖（覆盖式）
     */
    @PostMapping("/batch")
    public ApiResponse<Void> batchSave(@RequestParam Long workflowId,
                                        @RequestBody List<JobWorkflowDependency> dependencies) {
        // 检查工作流是否存在
        JobWorkflow workflow = workflowService.getById(workflowId);
        if (workflow == null) {
            return ApiResponse.error(400, "工作流不存在");
        }

        // 删除原有依赖
        dependencyService.remove(
                new LambdaQueryWrapper<JobWorkflowDependency>()
                        .eq(JobWorkflowDependency::getWorkflowId, workflowId)
        );

        // 批量插入新依赖
        if (dependencies != null && !dependencies.isEmpty()) {
            for (JobWorkflowDependency dep : dependencies) {
                dep.setWorkflowId(workflowId);
            }
            dependencyService.saveBatch(dependencies);
        }

        return ApiResponse.success();
    }
}
