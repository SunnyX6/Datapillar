package com.sunny.datapillar.platform.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户成员关系实体
 */
@Data
@TableName("tenant_users")
public class TenantUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_id")
    private Long userId;

    private Integer status;

    @TableField("is_default")
    private Integer isDefault;

    @TableField("token_sign")
    private String tokenSign;

    @TableField("token_expire_time")
    private LocalDateTime tokenExpireTime;

    @TableField("joined_at")
    private LocalDateTime joinedAt;
}
