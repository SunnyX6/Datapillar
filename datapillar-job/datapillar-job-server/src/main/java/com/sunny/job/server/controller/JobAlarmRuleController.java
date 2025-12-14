package com.sunny.job.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.job.server.common.ApiResponse;
import com.sunny.job.server.entity.JobAlarmRule;
import com.sunny.job.server.service.JobAlarmRuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警规则 Controller
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@RestController
@RequestMapping("/api/job/alarm-rule")
public class JobAlarmRuleController {

    private final JobAlarmRuleService ruleService;

    public JobAlarmRuleController(JobAlarmRuleService ruleService) {
        this.ruleService = ruleService;
    }

    /**
     * 查询告警规则列表（按命名空间）
     */
    @GetMapping("/list")
    public ApiResponse<List<JobAlarmRule>> list(@RequestParam Long namespaceId) {
        List<JobAlarmRule> list = ruleService.list(
                new LambdaQueryWrapper<JobAlarmRule>()
                        .eq(JobAlarmRule::getNamespaceId, namespaceId)
                        .orderByDesc(JobAlarmRule::getUpdatedAt)
        );
        return ApiResponse.success(list);
    }

    /**
     * 查询告警规则列表（按任务）
     */
    @GetMapping("/list/job")
    public ApiResponse<List<JobAlarmRule>> listByJob(@RequestParam Long jobId) {
        List<JobAlarmRule> list = ruleService.list(
                new LambdaQueryWrapper<JobAlarmRule>()
                        .eq(JobAlarmRule::getJobId, jobId)
        );
        return ApiResponse.success(list);
    }

    /**
     * 查询告警规则列表（按工作流）
     */
    @GetMapping("/list/workflow")
    public ApiResponse<List<JobAlarmRule>> listByWorkflow(@RequestParam Long workflowId) {
        List<JobAlarmRule> list = ruleService.list(
                new LambdaQueryWrapper<JobAlarmRule>()
                        .eq(JobAlarmRule::getWorkflowId, workflowId)
        );
        return ApiResponse.success(list);
    }

    /**
     * 根据 ID 查询
     */
    @GetMapping("/{id}")
    public ApiResponse<JobAlarmRule> getById(@PathVariable Long id) {
        JobAlarmRule rule = ruleService.getById(id);
        if (rule == null) {
            return ApiResponse.error(404, "告警规则不存在");
        }
        return ApiResponse.success(rule);
    }

    /**
     * 创建告警规则
     */
    @PostMapping
    public ApiResponse<JobAlarmRule> create(@RequestBody JobAlarmRule rule) {
        // 检查 jobId 和 workflowId 互斥
        if (rule.getJobId() != null && rule.getWorkflowId() != null) {
            return ApiResponse.error(400, "jobId 和 workflowId 不能同时设置");
        }
        if (rule.getJobId() == null && rule.getWorkflowId() == null) {
            return ApiResponse.error(400, "jobId 和 workflowId 必须设置其一");
        }

        rule.setRuleStatus(1);
        rule.setAlarmStatus(0);
        rule.setConsecutiveFails(0);
        ruleService.save(rule);
        return ApiResponse.success(rule);
    }

    /**
     * 更新告警规则
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody JobAlarmRule rule) {
        JobAlarmRule existing = ruleService.getById(id);
        if (existing == null) {
            return ApiResponse.error(404, "告警规则不存在");
        }

        rule.setId(id);
        ruleService.updateById(rule);
        return ApiResponse.success();
    }

    /**
     * 删除告警规则
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ruleService.removeById(id);
        return ApiResponse.success();
    }

    /**
     * 启用告警规则
     */
    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        JobAlarmRule rule = new JobAlarmRule();
        rule.setId(id);
        rule.setRuleStatus(1);
        ruleService.updateById(rule);
        return ApiResponse.success();
    }

    /**
     * 禁用告警规则
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        JobAlarmRule rule = new JobAlarmRule();
        rule.setId(id);
        rule.setRuleStatus(0);
        ruleService.updateById(rule);
        return ApiResponse.success();
    }
}
