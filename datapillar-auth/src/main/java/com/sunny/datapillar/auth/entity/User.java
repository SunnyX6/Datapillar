package com.sunny.datapillar.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户组件
 * 负责用户核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    private String username;

    @TableField("password")
    private String passwordHash;

    private String email;

    private String phone;

    private Integer status;  // 1:启用 0:禁用

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
