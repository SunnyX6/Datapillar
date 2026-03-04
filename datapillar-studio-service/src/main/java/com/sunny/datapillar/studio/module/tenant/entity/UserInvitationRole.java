package com.sunny.datapillar.studio.module.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * User invitation role component Responsible for implementing the core logic of user invitation
 * roles
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("user_invitation_roles")
public class UserInvitationRole {
  @TableId(type = IdType.AUTO)
  private Long id;

  @TableField("invitation_id")
  private Long invitationId;

  @TableField("role_id")
  private Long roleId;
}
