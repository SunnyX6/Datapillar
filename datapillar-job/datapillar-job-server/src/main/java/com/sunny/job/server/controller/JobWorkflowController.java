package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.entity.JobWorkflow;
import com.sunny.job.server.service.JobWorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 查询工作流列表
     */
    @GetMapping("/list")
    public ApiResponse<List<JobWorkflow>> list(@RequestParam Long namespaceId) {
        List<JobWorkflow> list = workflowService.list(
                new LambdaQueryWrapper<JobWorkflow>()
                        .eq(JobWorkflow::getNamespaceId, namespaceId)
                        .orderByDesc(JobWorkflow::getUpdatedAt)
        );
        return ApiResponse.success(list);
    }

    /**
     * 根据 ID 查询
     */
    @GetMapping("/{id}")
    public ApiResponse<JobWorkflow> getById(@PathVariable Long id) {
        JobWorkflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return ApiResponse.error(404, "工作流不存在");
        }
        return ApiResponse.success(workflow);
    }

    /**
     * 创建工作流
     */
    @PostMapping
    public ApiResponse<JobWorkflow> create(@RequestBody JobWorkflow workflow) {
        // 检查同一命名空间下名称是否重复
        long count = workflowService.count(
                new LambdaQueryWrapper<JobWorkflow>()
                        .eq(JobWorkflow::getNamespaceId, workflow.getNamespaceId())
                        .eq(JobWorkflow::getWorkflowName, workflow.getWorkflowName())
        );
        if (count > 0) {
            return ApiResponse.error(400, "工作流名称已存在");
        }

        workflow.setWorkflowStatus(0);
        workflowService.save(workflow);
        return ApiResponse.success(workflow);
    }

    /**
     * 更新工作流
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody JobWorkflow workflow) {
        JobWorkflow existing = workflowService.getById(id);
        if (existing == null) {
            return ApiResponse.error(404, "工作流不存在");
        }
        if (existing.isOnline()) {
            return ApiResponse.error(400, "上线状态的工作流不允许修改，请先下线");
        }

        workflow.setId(id);
        workflowService.updateById(workflow);
        return ApiResponse.success();
    }

    /**
     * 删除工作流
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        JobWorkflow existing = workflowService.getById(id);
        if (existing == null) {
            return ApiResponse.error(404, "工作流不存在");
        }
        if (existing.isOnline()) {
            return ApiResponse.error(400, "上线状态的工作流不允许删除，请先下线");
        }

        workflowService.removeById(id);
        return ApiResponse.success();
    }

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
}
