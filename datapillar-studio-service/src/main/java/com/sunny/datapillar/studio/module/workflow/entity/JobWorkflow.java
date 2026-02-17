package com.sunny.datapillar.studio.module.workflow.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 任务工作流组件
 * 负责任务工作流核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("job_workflow")
public class JobWorkflow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String workflowName;

    /**
     * 触发类型: 1-cron, 2-manual
     */
    private Integer triggerType;

    /**
     * 触发值（cron 表达式等）
     */
    private String triggerValue;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer priority;

    /**
     * 状态: 0-草稿, 1-已发布, 2-已暂停
     */
    private Integer status;

    private String description;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
