package com.sunny.datapillar.studio.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Task workflow components Responsible for the implementation of core logic of task workflow
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

  /** Trigger type: 1-cron, 2-manual */
  private Integer triggerType;

  /** trigger value（cron Expressions etc.） */
  private String triggerValue;

  private Integer timeoutSeconds;

  private Integer maxRetryTimes;

  private Integer priority;

  /** Status: 0-draft, 1-Published, 2-Suspended */
  private Integer status;

  private String description;

  @TableLogic private Integer isDeleted;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
