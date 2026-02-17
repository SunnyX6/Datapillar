package com.sunny.datapillar.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 租户单点登录配置
 * 负责租户单点登录配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("tenant_sso_configs")
public class TenantSsoConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    private String provider;

    private Integer status;

    @TableField("base_url")
    private String baseUrl;

    @TableField("config_json")
    private String configJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
