package com.sunny.datapillar.studio.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sunny.datapillar.studio.module.project.enums.ProjectStatus;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Project components Responsible for the implementation of the core logic of the project
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("projects")
public class Project {

  /** ProjectID */
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  /** Project name */
  @TableField("name")
  private String name;

  /** Project description */
  @TableField("description")
  private String description;

  /** project ownerID */
  @TableField("owner_id")
  private Long ownerId;

  /** Project status */
  @TableField("status")
  private ProjectStatus status;

  /** Project tags（JSONformat storage） */
  @TableField("tags")
  private String tags;

  /** Whether to collect */
  @TableField("is_favorite")
  private Boolean isFavorite;

  /** visible or not */
  @TableField("is_visible")
  private Boolean isVisible;

  /** Number of members */
  @TableField("member_count")
  private Integer memberCount;

  /** last access time */
  @TableField("last_accessed_at")
  private LocalDateTime lastAccessedAt;

  /** creation time */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** Update time */
  @TableField("updated_at")
  private LocalDateTime updatedAt;

  /** tombstone flag */
  @TableLogic
  @TableField("deleted")
  private Boolean deleted;
}
