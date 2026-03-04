package com.sunny.datapillar.studio.module.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * User invitation component Responsible for implementing the core logic of user invitations
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("user_invitations")
public class UserInvitation {
  @TableId(type = IdType.AUTO)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("inviter_user_id")
  private Long inviterUserId;

  @TableField("invite_code")
  private String inviteCode;

  private Integer status;

  @TableField("expires_at")
  private LocalDateTime expiresAt;

  @TableField("accepted_user_id")
  private Long acceptedUserId;

  @TableField("accepted_at")
  private LocalDateTime acceptedAt;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
