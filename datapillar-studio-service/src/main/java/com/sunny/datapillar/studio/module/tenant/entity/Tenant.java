package com.sunny.datapillar.studio.module.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 租户实体
 */
@Data
@TableName("tenants")
public class Tenant {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("parent_id")
    private Long parentId;

    private String code;

    private String name;

    private String type;

    @TableField("encrypt_public_key")
    private String encryptPublicKey;

    private Integer status;

    private Integer level;

    private String path;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
