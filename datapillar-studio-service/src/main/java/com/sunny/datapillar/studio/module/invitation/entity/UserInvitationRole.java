package com.sunny.datapillar.studio.module.invitation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 邀请关联角色
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
