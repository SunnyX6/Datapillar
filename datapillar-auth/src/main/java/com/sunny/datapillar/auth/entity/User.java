package com.sunny.datapillar.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 - 认证中心只操作此表
 * 只包含身份验证相关信息,不包含角色和权限
 */
@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @TableField("password")
    private String passwordHash;

    private String email;

    private Integer status;  // 1:启用 0:禁用

    @TableField("token_sign")
    private String tokenSign;  // 登录Token签名（用于SSO验证和Token撤销）

    @TableField("token_expire_time")
    private LocalDateTime tokenExpireTime;  // Token过期时间

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
