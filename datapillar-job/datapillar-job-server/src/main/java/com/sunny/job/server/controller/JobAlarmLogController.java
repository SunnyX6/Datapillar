package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.entity.JobAlarmLog;
import com.sunny.job.server.service.JobAlarmLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警记录 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/alarm-log")
public class JobAlarmLogController {

    private final JobAlarmLogService alarmLogService;

    public JobAlarmLogController(JobAlarmLogService alarmLogService) {
        this.alarmLogService = alarmLogService;
    }

    /**
     * 分页查询告警记录（按命名空间）
     */
    @GetMapping("/page")
    public ApiResponse<Page<JobAlarmLog>> page(
            @RequestParam Long namespaceId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer sendStatus) {

        LambdaQueryWrapper<JobAlarmLog> wrapper = new LambdaQueryWrapper<JobAlarmLog>()
                .eq(JobAlarmLog::getNamespaceId, namespaceId);

        if (sendStatus != null) {
            wrapper.eq(JobAlarmLog::getSendStatus, sendStatus);
        }

        wrapper.orderByDesc(JobAlarmLog::getCreatedAt);

        Page<JobAlarmLog> page = alarmLogService.page(new Page<>(pageNum, pageSize), wrapper);
        return ApiResponse.success(page);
    }

    /**
     * 查询告警记录（按任务执行实例）
     */
    @GetMapping("/list/job-run")
    public ApiResponse<List<JobAlarmLog>> listByJobRun(@RequestParam Long jobRunId) {
        List<JobAlarmLog> list = alarmLogService.list(
                new LambdaQueryWrapper<JobAlarmLog>()
                        .eq(JobAlarmLog::getJobRunId, jobRunId)
                        .orderByDesc(JobAlarmLog::getCreatedAt)
        );
        return ApiResponse.success(list);
    }

    /**
     * 查询告警记录（按工作流执行实例）
     */
    @GetMapping("/list/workflow-run")
    public ApiResponse<List<JobAlarmLog>> listByWorkflowRun(@RequestParam Long workflowRunId) {
        List<JobAlarmLog> list = alarmLogService.list(
                new LambdaQueryWrapper<JobAlarmLog>()
                        .eq(JobAlarmLog::getWorkflowRunId, workflowRunId)
                        .orderByDesc(JobAlarmLog::getCreatedAt)
        );
        return ApiResponse.success(list);
    }

    /**
     * 查询告警记录（按规则）
     */
    @GetMapping("/list/rule")
    public ApiResponse<List<JobAlarmLog>> listByRule(@RequestParam Long ruleId) {
        List<JobAlarmLog> list = alarmLogService.list(
                new LambdaQueryWrapper<JobAlarmLog>()
                        .eq(JobAlarmLog::getRuleId, ruleId)
                        .orderByDesc(JobAlarmLog::getCreatedAt)
                        .last("LIMIT 100")
        );
        return ApiResponse.success(list);
    }

    /**
     * 根据 ID 查询
     */
    @GetMapping("/{id}")
    public ApiResponse<JobAlarmLog> getById(@PathVariable Long id) {
        JobAlarmLog log = alarmLogService.getById(id);
        if (log == null) {
            return ApiResponse.error(404, "告警记录不存在");
        }
        return ApiResponse.success(log);
    }
}
