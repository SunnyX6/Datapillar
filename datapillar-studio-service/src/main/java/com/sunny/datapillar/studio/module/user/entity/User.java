package com.sunny.datapillar.studio.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private Integer deleted;

    @TableField(exist = false)
    private String tokenSign;  // 已迁移到 tenant_users，保留字段避免旧逻辑误用

    @TableField(exist = false)
    private LocalDateTime tokenExpireTime;  // 已迁移到 tenant_users

    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
