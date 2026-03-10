package com.sunny.datapillar.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Tenant API key entity. */
@Data
@TableName("tenant_api_keys")
public class TenantApiKey {

  @TableId(type = IdType.AUTO)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  private String name;

  private String description;

  @TableField("key_hash")
  private String keyHash;

  @TableField("last_four")
  private String lastFour;

  private Integer status;

  @TableField("expires_at")
  private LocalDateTime expiresAt;

  @TableField("last_used_at")
  private LocalDateTime lastUsedAt;

  @TableField("last_used_ip")
  private String lastUsedIp;

  @TableField("created_by")
  private Long createdBy;

  @TableField("disabled_by")
  private Long disabledBy;

  @TableField("disabled_at")
  private LocalDateTime disabledAt;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
