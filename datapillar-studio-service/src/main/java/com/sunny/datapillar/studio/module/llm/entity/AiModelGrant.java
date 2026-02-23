package com.sunny.datapillar.studio.module.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI模型授权实体
 * 负责AI模型授权核心逻辑实现
 *
 * @author Sunny
 * @date 2026-02-22
 */
@Data
@TableName("ai_model_grant")
public class AiModelGrant {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_id")
    private Long userId;

    @TableField("model_id")
    private Long modelId;

    @TableField("permission_id")
    private Long permissionId;

    @TableField("is_default")
    private Boolean isDefault;

    @TableField("granted_by")
    private Long grantedBy;

    @TableField("granted_at")
    private LocalDateTime grantedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;
}
