package com.sunny.datapillar.studio.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色权限组件
 * 负责角色权限核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("role_permissions")
public class RolePermission {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("role_id")
    private Long roleId;

    @TableField("object_id")
    private Long objectId;

    @TableField("permission_id")
    private Long permissionId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
