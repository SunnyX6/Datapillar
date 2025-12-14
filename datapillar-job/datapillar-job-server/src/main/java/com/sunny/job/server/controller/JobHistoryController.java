package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.entity.JobRun;
import com.sunny.job.server.entity.JobWorkflowRun;
import com.sunny.job.server.service.JobRunService;
import com.sunny.job.server.service.JobWorkflowRunService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 执行历史 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/history")
public class JobHistoryController {

    private final JobWorkflowRunService workflowRunService;
    private final JobRunService jobRunService;

    public JobHistoryController(JobWorkflowRunService workflowRunService,
                                 JobRunService jobRunService) {
        this.workflowRunService = workflowRunService;
        this.jobRunService = jobRunService;
    }

    // ==================== WorkflowRun ====================

    /**
     * 分页查询工作流执行历史
     */
    @GetMapping("/workflow-run/page")
    public ApiResponse<Page<JobWorkflowRun>> pageWorkflowRun(
            @RequestParam Long workflowId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        Page<JobWorkflowRun> page = workflowRunService.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<JobWorkflowRun>()
                        .eq(JobWorkflowRun::getWorkflowId, workflowId)
                        .orderByDesc(JobWorkflowRun::getTriggerTime)
        );
        return ApiResponse.success(page);
    }

    /**
     * 根据 ID 查询工作流执行详情
     */
    @GetMapping("/workflow-run/{id}")
    public ApiResponse<JobWorkflowRun> getWorkflowRunById(@PathVariable Long id) {
        JobWorkflowRun workflowRun = workflowRunService.getById(id);
        if (workflowRun == null) {
            return ApiResponse.error(404, "工作流执行实例不存在");
        }
        return ApiResponse.success(workflowRun);
    }

    /**
     * 重跑工作流实例
     */
    @PostMapping("/workflow-run/{id}/rerun")
    public ApiResponse<Void> rerunWorkflowRun(@PathVariable Long id) {
        workflowRunService.rerun(id);
        return ApiResponse.success();
    }

    // ==================== JobRun ====================

    /**
     * 查询工作流实例下的所有任务执行
     */
    @GetMapping("/job-run/list")
    public ApiResponse<List<JobRun>> listJobRun(@RequestParam Long workflowRunId) {
        List<JobRun> list = jobRunService.list(
                new LambdaQueryWrapper<JobRun>()
                        .eq(JobRun::getWorkflowRunId, workflowRunId)
                        .orderByAsc(JobRun::getId)
        );
        return ApiResponse.success(list);
    }

    /**
     * 分页查询任务执行历史（按任务定义）
     */
    @GetMapping("/job-run/page")
    public ApiResponse<Page<JobRun>> pageJobRun(
            @RequestParam Long jobId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        Page<JobRun> page = jobRunService.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<JobRun>()
                        .eq(JobRun::getJobId, jobId)
                        .orderByDesc(JobRun::getTriggerTime)
        );
        return ApiResponse.success(page);
    }

    /**
     * 根据 ID 查询任务执行详情
     */
    @GetMapping("/job-run/{id}")
    public ApiResponse<JobRun> getJobRunById(@PathVariable Long id) {
        JobRun jobRun = jobRunService.getById(id);
        if (jobRun == null) {
            return ApiResponse.error(404, "任务执行实例不存在");
        }
        return ApiResponse.success(jobRun);
    }
}
