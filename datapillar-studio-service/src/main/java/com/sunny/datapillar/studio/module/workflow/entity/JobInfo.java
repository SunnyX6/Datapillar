package com.sunny.datapillar.studio.module.workflow.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;

import lombok.Data;

import java.util.Map;

/**
 * 任务实体 - 对应 job_info 表
 *
 * @author sunny
 */
@Data
@TableName(value = "job_info", autoResultMap = true)
public class JobInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private String jobName;

    /**
     * 任务类型 - 对应 job_component.id
     */
    private Long jobType;

    /**
     * 任务参数 JSON
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> jobParams;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer retryInterval;

    private Integer priority;

    /**
     * 画布 X 坐标
     */
    private Double positionX;

    /**
     * 画布 Y 坐标
     */
    private Double positionY;

    private String description;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
