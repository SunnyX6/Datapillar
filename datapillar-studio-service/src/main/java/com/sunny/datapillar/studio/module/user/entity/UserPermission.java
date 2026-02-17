package com.sunny.datapillar.studio.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户权限组件
 * 负责用户权限核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("user_permission_overrides")
public class UserPermission {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("object_id")
    private Long objectId;

    @TableField("permission_id")
    private Long permissionId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
