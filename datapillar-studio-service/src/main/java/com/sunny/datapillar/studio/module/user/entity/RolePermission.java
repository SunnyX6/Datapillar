package com.sunny.datapillar.studio.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 角色权限关联实体类
 *
 * @author sunny
 * @since 2024-01-01
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
