package com.sunny.datapillar.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户身份映射
 */
@Data
@TableName("user_identities")
public class UserIdentity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_id")
    private Long userId;

    private String provider;

    @TableField("external_user_id")
    private String externalUserId;

    @TableField("union_id")
    private String unionId;

    @TableField("open_id")
    private String openId;

    private String email;

    private String mobile;

    @TableField("profile_json")
    private String profileJson;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
