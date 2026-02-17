package com.sunny.datapillar.studio.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户用户组件
 * 负责租户用户核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
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


    @TableField("joined_at")
    private LocalDateTime joinedAt;
}
