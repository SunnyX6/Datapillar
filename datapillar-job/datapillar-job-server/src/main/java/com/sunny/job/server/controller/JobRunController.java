package com.sunny.job.server.controller;

import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.common.ParamValidator;
import com.sunny.job.server.service.JobRunService;
import org.springframework.web.bind.annotation.*;

/**
 * 任务运行实例 Controller
 * <p>
 * 处理工作流运行实例中单个任务的操作：kill、pass、markFailed、retry、trigger
 * <p>
 * API 路径：/api/job/{workflowRunId}/job-run/{jobRunId}
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@RestController
@RequestMapping("/api/job/{workflowRunId}/job-run")
public class JobRunController {

    private final JobRunService jobRunService;

    public JobRunController(JobRunService jobRunService) {
        this.jobRunService = jobRunService;
    }

    /**
     * 终止任务
     */
    @PostMapping("/{jobRunId}/kill")
    public ApiResponse<Void> kill(@PathVariable Long workflowRunId,
                                  @PathVariable Long jobRunId) {
        ParamValidator.requirePositive(workflowRunId, "workflowRunId");
        ParamValidator.requirePositive(jobRunId, "jobRunId");

        jobRunService.kill(jobRunId);
        return ApiResponse.success();
    }

    /**
     * 跳过任务
     * <p>
     * 将任务标记为已跳过，视为成功，触发下游任务
     */
    @PostMapping("/{jobRunId}/pass")
    public ApiResponse<Void> pass(@PathVariable Long workflowRunId,
                                  @PathVariable Long jobRunId) {
        ParamValidator.requirePositive(workflowRunId, "workflowRunId");
        ParamValidator.requirePositive(jobRunId, "jobRunId");

        jobRunService.pass(jobRunId);
        return ApiResponse.success();
    }

    /**
     * 标记任务失败
     */
    @PostMapping("/{jobRunId}/mark-failed")
    public ApiResponse<Void> markFailed(@PathVariable Long workflowRunId,
                                        @PathVariable Long jobRunId) {
        ParamValidator.requirePositive(workflowRunId, "workflowRunId");
        ParamValidator.requirePositive(jobRunId, "jobRunId");

        jobRunService.markFailed(jobRunId);
        return ApiResponse.success();
    }

    /**
     * 重试任务
     */
    @PostMapping("/{jobRunId}/retry")
    public ApiResponse<Void> retry(@PathVariable Long workflowRunId,
                                   @PathVariable Long jobRunId,
                                   @RequestBody JobRunOpRequest request) {
        ParamValidator.requirePositive(workflowRunId, "workflowRunId");
        ParamValidator.requirePositive(jobRunId, "jobRunId");
        ParamValidator.requirePositive(request.getJobId(), "jobId");
        ParamValidator.requirePositive(request.getNamespaceId(), "namespaceId");
        ParamValidator.requireNotNull(request.getBucketId(), "bucketId");

        jobRunService.retry(jobRunId, request.getJobId(), workflowRunId,
                request.getNamespaceId(), request.getBucketId());
        return ApiResponse.success();
    }

    /**
     * 手动触发任务
     */
    @PostMapping("/{jobRunId}/trigger")
    public ApiResponse<Void> trigger(@PathVariable Long workflowRunId,
                                     @PathVariable Long jobRunId,
                                     @RequestBody JobRunOpRequest request) {
        ParamValidator.requirePositive(workflowRunId, "workflowRunId");
        ParamValidator.requirePositive(jobRunId, "jobRunId");
        ParamValidator.requirePositive(request.getJobId(), "jobId");
        ParamValidator.requirePositive(request.getNamespaceId(), "namespaceId");
        ParamValidator.requireNotNull(request.getBucketId(), "bucketId");

        jobRunService.trigger(jobRunId, request.getJobId(), workflowRunId,
                request.getNamespaceId(), request.getBucketId());
        return ApiResponse.success();
    }

    /**
     * 任务操作请求体
     * <p>
     * 用于 retry 和 trigger 操作，需要额外参数用于路由到正确的 Worker
     */
    public static class JobRunOpRequest {
        private Long jobId;
        private Long namespaceId;
        private Integer bucketId;

        public Long getJobId() {
            return jobId;
        }

        public void setJobId(Long jobId) {
            this.jobId = jobId;
        }

        public Long getNamespaceId() {
            return namespaceId;
        }

        public void setNamespaceId(Long namespaceId) {
            this.namespaceId = namespaceId;
        }

        public Integer getBucketId() {
            return bucketId;
        }

        public void setBucketId(Integer bucketId) {
            this.bucketId = bucketId;
        }
    }
}
