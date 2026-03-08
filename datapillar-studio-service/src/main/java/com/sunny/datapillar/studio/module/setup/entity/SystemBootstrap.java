package com.sunny.datapillar.studio.module.setup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * System boot components Responsible for system guidance core logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("system_bootstrap")
public class SystemBootstrap {

  @TableId(type = IdType.INPUT)
  private Integer id;

  @TableField("status")
  private Integer status;

  @TableField("setup_tenant_id")
  private Long setupTenantId;

  @TableField("setup_admin_user_id")
  private Long setupAdminUserId;

  @TableField("setup_token_hash")
  private String setupTokenHash;

  @TableField("setup_token_generated_at")
  private LocalDateTime setupTokenGeneratedAt;

  @TableField("setup_completed_at")
  private LocalDateTime setupCompletedAt;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
