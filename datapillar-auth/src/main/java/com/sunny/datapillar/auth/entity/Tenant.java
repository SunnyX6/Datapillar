package com.sunny.datapillar.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Tenant entity.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@TableName("tenants")
public class Tenant {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String code;

  private String name;

  @TableField("encrypt_public_key")
  private String encryptPublicKey;

  private Integer status; // 1: enabled, 0: disabled
}
