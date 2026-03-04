package com.sunny.datapillar.studio.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * TaskInfocomponents Responsible for tasksInfoCore logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName(value = "job_info", autoResultMap = true)
public class JobInfo {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long workflowId;

  private String jobName;

  /** Task type - Correspond job_component.id */
  private Long jobType;

  /** Task parameters JSON */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> jobParams;

  private Integer timeoutSeconds;

  private Integer maxRetryTimes;

  private Integer retryInterval;

  private Integer priority;

  /** canvas X coordinates */
  private Double positionX;

  /** canvas Y coordinates */
  private Double positionY;

  private String description;

  @TableLogic private Integer isDeleted;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
