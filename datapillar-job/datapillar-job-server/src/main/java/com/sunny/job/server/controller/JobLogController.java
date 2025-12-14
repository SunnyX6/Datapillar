package com.sunny.job.server.controller;

import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.service.JobLogService;
import com.sunny.job.server.service.JobLogService.LogResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务日志 Controller
 * <p>
 * 提供任务执行日志的查询接口，支持增量读取（实时 tail）
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/log")
public class JobLogController {

    private final JobLogService logService;

    public JobLogController(JobLogService logService) {
        this.logService = logService;
    }

    /**
     * 读取日志内容（支持增量读取）
     * <p>
     * 前端轮询实现实时日志：
     * 1. 首次请求 offset=0
     * 2. 后续请求使用返回的 offset 值
     * 3. hasMore=false 且任务已完成时停止轮询
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数（默认0，表示首次执行）
     * @param offset      起始偏移量（字节），0 表示从头开始
     * @param limit       最大读取字节数（默认64KB，最大1MB）
     */
    @GetMapping("/read")
    public ApiResponse<LogResult> readLog(
            @RequestParam Long namespaceId,
            @RequestParam Long jobRunId,
            @RequestParam(defaultValue = "0") Integer retryCount,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "65536") Integer limit) {

        // 限制最大读取字节数为 1MB
        int safeLimit = Math.min(limit, 1024 * 1024);

        LogResult result = logService.readLog(namespaceId, jobRunId, retryCount, offset, safeLimit);
        return ApiResponse.success(result);
    }

    /**
     * 读取完整日志内容
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数（默认0，表示首次执行）
     */
    @GetMapping("/full")
    public ApiResponse<String> readFullLog(
            @RequestParam Long namespaceId,
            @RequestParam Long jobRunId,
            @RequestParam(defaultValue = "0") Integer retryCount) {

        String content = logService.readFullLog(namespaceId, jobRunId, retryCount);
        if (content == null) {
            return ApiResponse.error("日志文件不存在");
        }
        return ApiResponse.success(content);
    }

    /**
     * 获取任务的所有重试记录
     * <p>
     * 用于展示任务的历史执行记录（首次执行 + 多次重试）
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     */
    @GetMapping("/attempts")
    public ApiResponse<List<Integer>> listAttempts(
            @RequestParam Long namespaceId,
            @RequestParam Long jobRunId) {

        List<Integer> attempts = logService.listRetryAttempts(namespaceId, jobRunId);
        return ApiResponse.success(attempts);
    }

    /**
     * 检查日志文件是否存在
     *
     * @param namespaceId 命名空间ID
     * @param jobRunId    任务执行实例ID
     * @param retryCount  重试次数（默认0，表示首次执行）
     */
    @GetMapping("/exists")
    public ApiResponse<Boolean> exists(
            @RequestParam Long namespaceId,
            @RequestParam Long jobRunId,
            @RequestParam(defaultValue = "0") Integer retryCount) {

        boolean exists = logService.exists(namespaceId, jobRunId, retryCount);
        return ApiResponse.success(exists);
    }
}
