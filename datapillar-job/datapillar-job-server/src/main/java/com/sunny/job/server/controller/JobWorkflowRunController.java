package com.sunny.job.server.controller;

import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.service.JobWorkflowRunService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 工作流运行实例 Controller
 * <p>
 * 处理运行实例级别的操作：kill、rerun
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@RestController
@RequestMapping("/api/job/workflow-run")
public class JobWorkflowRunController {

    private final JobWorkflowRunService workflowRunService;

    public JobWorkflowRunController(JobWorkflowRunService workflowRunService) {
        this.workflowRunService = workflowRunService;
    }

    /**
     * 终止工作流运行实例
     */
    @PostMapping("/{id}/kill")
    public ApiResponse<Void> kill(@PathVariable Long id) {
        workflowRunService.kill(id);
        return ApiResponse.success();
    }

    /**
     * 重跑工作流运行实例
     */
    @PostMapping("/{id}/rerun")
    public ApiResponse<Void> rerun(@PathVariable Long id, @RequestBody RerunRequest request) {
        workflowRunService.rerun(request.getWorkflowId(), id, request.getJobRunIdToJobIdMap());
        return ApiResponse.success();
    }

    /**
     * 重跑请求体
     */
    public static class RerunRequest {
        private Long workflowId;
        private Map<Long, Long> jobRunIdToJobIdMap;

        public Long getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(Long workflowId) {
            this.workflowId = workflowId;
        }

        public Map<Long, Long> getJobRunIdToJobIdMap() {
            return jobRunIdToJobIdMap;
        }

        public void setJobRunIdToJobIdMap(Map<Long, Long> jobRunIdToJobIdMap) {
            this.jobRunIdToJobIdMap = jobRunIdToJobIdMap;
        }
    }
}
