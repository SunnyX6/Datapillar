package com.sunny.datapillar.studio.module.features.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户功能授权审计
 */
@Data
@TableName("tenant_feature_audit")
public class TenantFeatureAudit {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("object_id")
    private Long objectId;

    private String action;

    @TableField("before_status")
    private Integer beforeStatus;

    @TableField("after_status")
    private Integer afterStatus;

    @TableField("before_permission_id")
    private Long beforePermissionId;

    @TableField("after_permission_id")
    private Long afterPermissionId;

    @TableField("operator_user_id")
    private Long operatorUserId;

    @TableField("operator_tenant_id")
    private Long operatorTenantId;

    @TableField("request_id")
    private String requestId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
