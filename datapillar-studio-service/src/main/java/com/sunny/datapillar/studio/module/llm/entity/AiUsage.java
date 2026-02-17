package com.sunny.datapillar.studio.module.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AIUsage组件
 * 负责AIUsage核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("ai_usage")
public class AiUsage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_id")
    private Long userId;

    @TableField("model_id")
    private Long modelId;

    @TableField("status")
    private Integer status;

    @TableField("is_default")
    private Boolean isDefault;

    @TableField("total_cost_usd")
    private BigDecimal totalCostUsd;

    @TableField("granted_by")
    private Long grantedBy;

    @TableField("granted_at")
    private LocalDateTime grantedAt;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
