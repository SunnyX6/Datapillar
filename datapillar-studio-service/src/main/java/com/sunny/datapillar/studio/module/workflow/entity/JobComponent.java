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
 * TaskComponentcomponents Responsible for tasksComponentCore logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName(value = "job_component", autoResultMap = true)
public class JobComponent {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** component code（Such as SHELL, PYTHON, SQL） */
  private String componentCode;

  private String componentName;

  private String componentType;

  /** Parameter definition（JSON Schema） */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> jobParams;

  private String description;

  private String icon;

  private String color;

  private Integer status;

  private Integer sortOrder;

  @TableLogic private Integer isDeleted;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
