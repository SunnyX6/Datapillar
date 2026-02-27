package com.sunny.datapillar.studio.module.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sunny.datapillar.studio.module.llm.enums.AiModelStatus;
import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AIModel组件
 * 负责AIModel核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("ai_model")
public class AiModel {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("provider_model_id")
    private String providerModelId;

    @TableField("name")
    private String name;

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_type")
    private AiModelType modelType;

    @TableField("description")
    private String description;

    @TableField("tags")
    private String tags;

    @TableField("context_tokens")
    private Integer contextTokens;

    @TableField("input_price_usd")
    private BigDecimal inputPriceUsd;

    @TableField("output_price_usd")
    private BigDecimal outputPriceUsd;

    @TableField("embedding_dimension")
    private Integer embeddingDimension;

    @TableField("api_key")
    private String apiKey;

    @TableField("base_url")
    private String baseUrl;

    @TableField("status")
    private AiModelStatus status;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
