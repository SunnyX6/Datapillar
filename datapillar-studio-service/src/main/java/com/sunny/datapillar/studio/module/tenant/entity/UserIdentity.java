package com.sunny.datapillar.studio.module.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * UserIdentitycomponents Responsible userIdentityCore logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("user_identities")
public class UserIdentity {

  @TableId(type = IdType.AUTO)
  private Long id;

  @TableField(exist = false)
  private Long tenantId;

  @TableField("user_id")
  private Long userId;

  @TableField(exist = false)
  private String provider;

  @TableField(exist = false)
  private String externalUserId;

  private String issuer;

  private String subject;

  @TableField("identity_key")
  private String identityKey;

  @TableField("username_snapshot")
  private String usernameSnapshot;

  @TableField("email_snapshot")
  private String emailSnapshot;

  @TableField("profile_json")
  private String profileJson;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
