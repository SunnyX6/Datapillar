package com.sunny.datapillar.studio.module.setup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 系统引导组件
 * 负责系统引导核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("system_bootstrap")
public class SystemBootstrap {

    @TableId(type = IdType.INPUT)
    private Integer id;

    @TableField("setup_completed")
    private Integer setupCompleted;

    @TableField("setup_tenant_id")
    private Long setupTenantId;

    @TableField("setup_admin_user_id")
    private Long setupAdminUserId;

    @TableField("setup_token_hash")
    private String setupTokenHash;

    @TableField("setup_token_generated_at")
    private LocalDateTime setupTokenGeneratedAt;

    @TableField("setup_completed_at")
    private LocalDateTime setupCompletedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
