package com.sunny.datapillar.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 组织成员关系
 */
@Data
@TableName("org_users")
public class OrgUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("org_id")
    private Long orgId;

    @TableField("user_id")
    private Long userId;

    private Integer status;

    @TableField("joined_at")
    private LocalDateTime joinedAt;
}
