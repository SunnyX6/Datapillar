package com.sunny.datapillar.studio.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * TaskDependencycomponents Responsible for tasksDependencyCore logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("job_dependency")
public class JobDependency {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long workflowId;

  /** current task ID */
  private Long jobId;

  /** upstream tasks ID（parent node） */
  private Long parentJobId;

  @TableLogic private Integer isDeleted;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
