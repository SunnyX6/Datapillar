package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.entity.JobInfo;
import com.sunny.job.server.entity.JobWorkflow;
import com.sunny.job.server.service.JobInfoService;
import com.sunny.job.server.service.JobWorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务定义 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/info")
public class JobInfoController {

    private final JobInfoService jobInfoService;
    private final JobWorkflowService workflowService;

    public JobInfoController(JobInfoService jobInfoService, JobWorkflowService workflowService) {
        this.jobInfoService = jobInfoService;
        this.workflowService = workflowService;
    }

    /**
     * 查询任务列表（按工作流）
     */
    @GetMapping("/list")
    public ApiResponse<List<JobInfo>> list(@RequestParam Long workflowId) {
        List<JobInfo> list = jobInfoService.list(
                new LambdaQueryWrapper<JobInfo>()
                        .eq(JobInfo::getWorkflowId, workflowId)
                        .orderByAsc(JobInfo::getId)
        );
        return ApiResponse.success(list);
    }

    /**
     * 根据 ID 查询
     */
    @GetMapping("/{id}")
    public ApiResponse<JobInfo> getById(@PathVariable Long id) {
        JobInfo job = jobInfoService.getById(id);
        if (job == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        return ApiResponse.success(job);
    }

    /**
     * 创建任务
     */
    @PostMapping
    public ApiResponse<JobInfo> create(@RequestBody JobInfo job) {
        // 检查工作流是否存在
        JobWorkflow workflow = workflowService.getById(job.getWorkflowId());
        if (workflow == null) {
            return ApiResponse.error(400, "工作流不存在");
        }
        if (workflow.isOnline()) {
            return ApiResponse.error(400, "上线状态的工作流不允许新增任务，请先下线");
        }

        // 检查同一工作流下名称是否重复
        long count = jobInfoService.count(
                new LambdaQueryWrapper<JobInfo>()
                        .eq(JobInfo::getWorkflowId, job.getWorkflowId())
                        .eq(JobInfo::getJobName, job.getJobName())
        );
        if (count > 0) {
            return ApiResponse.error(400, "任务名称已存在");
        }

        job.setNamespaceId(workflow.getNamespaceId());
        job.setJobStatus(1);
        jobInfoService.save(job);
        return ApiResponse.success(job);
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody JobInfo job) {
        JobInfo existing = jobInfoService.getById(id);
        if (existing == null) {
            return ApiResponse.error(404, "任务不存在");
        }

        JobWorkflow workflow = workflowService.getById(existing.getWorkflowId());
        if (workflow != null && workflow.isOnline()) {
            return ApiResponse.error(400, "上线状态的工作流不允许修改任务，请先下线");
        }

        job.setId(id);
        jobInfoService.updateById(job);
        return ApiResponse.success();
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        JobInfo existing = jobInfoService.getById(id);
        if (existing == null) {
            return ApiResponse.error(404, "任务不存在");
        }

        JobWorkflow workflow = workflowService.getById(existing.getWorkflowId());
        if (workflow != null && workflow.isOnline()) {
            return ApiResponse.error(400, "上线状态的工作流不允许删除任务，请先下线");
        }

        jobInfoService.removeById(id);
        return ApiResponse.success();
    }

    /**
     * 启用任务
     */
    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        JobInfo job = new JobInfo();
        job.setId(id);
        job.setJobStatus(1);
        jobInfoService.updateById(job);
        return ApiResponse.success();
    }

    /**
     * 禁用任务
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        JobInfo job = new JobInfo();
        job.setId(id);
        job.setJobStatus(0);
        jobInfoService.updateById(job);
        return ApiResponse.success();
    }
}
