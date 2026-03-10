package com.sunny.datapillar.openlineage.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** ai_embedding_binding row. */
@Data
@TableName("ai_embedding_binding")
public class EmbeddingBindingEntity {

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("scope")
  private String scope;

  @TableField("owner_user_id")
  private Long ownerUserId;

  @TableField("ai_model_id")
  private Long aiModelId;

  @TableField("revision")
  private Long revision;

  @TableField("set_by")
  private Long setBy;

  @TableField("set_at")
  private LocalDateTime setAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
