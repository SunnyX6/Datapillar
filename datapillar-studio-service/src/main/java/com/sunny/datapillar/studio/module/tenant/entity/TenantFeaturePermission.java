package com.sunny.datapillar.studio.module.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户功能权限组件
 * 负责租户功能权限核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("tenant_feature_permissions")
public class TenantFeaturePermission {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("object_id")
    private Long objectId;

    @TableField("permission_id")
    private Long permissionId;

    private Integer status;

    @TableField("grant_source")
    private String grantSource;

    @TableField("granted_by")
    private Long grantedBy;

    @TableField("granted_at")
    private LocalDateTime grantedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
