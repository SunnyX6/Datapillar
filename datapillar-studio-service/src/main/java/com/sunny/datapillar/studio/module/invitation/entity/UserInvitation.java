package com.sunny.datapillar.studio.module.invitation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户邀请
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

    @TableField("invitee_email")
    private String inviteeEmail;

    @TableField("invitee_mobile")
    private String inviteeMobile;

    @TableField("invitee_key")
    private String inviteeKey;

    @TableField("active_invitee_key")
    private String activeInviteeKey;

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
