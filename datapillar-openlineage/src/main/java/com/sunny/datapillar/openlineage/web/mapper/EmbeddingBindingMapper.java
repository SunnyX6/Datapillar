package com.sunny.datapillar.openlineage.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.openlineage.web.entity.EmbeddingBindingEntity;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Mapper for ai_embedding_binding and DW embedding runtime lookup. */
@Mapper
public interface EmbeddingBindingMapper extends BaseMapper<EmbeddingBindingEntity> {

  @Select(
      """
      SELECT
        m.id AS ai_model_id,
        m.tenant_id,
        m.provider_model_id,
        m.model_type,
        m.embedding_dimension,
        m.api_key,
        COALESCE(m.base_url, p.base_url) AS base_url,
        m.status,
        p.code AS provider_code
      FROM ai_model m
      JOIN ai_provider p ON p.id = m.provider_id
      WHERE m.id = #{aiModelId} AND m.tenant_id = #{tenantId}
      """)
  RuntimeModelRow selectModelRuntimeById(
      @Param("tenantId") Long tenantId, @Param("aiModelId") Long aiModelId);

  @Select(
      """
      SELECT
        b.revision,
        b.ai_model_id,
        m.tenant_id,
        m.provider_model_id,
        m.model_type,
        m.embedding_dimension,
        m.api_key,
        COALESCE(m.base_url, p.base_url) AS base_url,
        m.status,
        p.code AS provider_code
      FROM ai_embedding_binding b
      JOIN ai_model m ON m.id = b.ai_model_id AND m.tenant_id = b.tenant_id
      JOIN ai_provider p ON p.id = m.provider_id
      WHERE b.tenant_id = #{tenantId} AND b.scope = #{scope} AND b.owner_user_id = #{ownerUserId}
      ORDER BY b.id ASC
      """)
  List<RuntimeModelRow> selectDwRuntimeByTenant(
      @Param("tenantId") Long tenantId,
      @Param("scope") String scope,
      @Param("ownerUserId") Long ownerUserId);

  @Select(
      """
      SELECT id,tenant_id,scope,owner_user_id,ai_model_id,revision,set_by,set_at,updated_at
      FROM ai_embedding_binding
      WHERE tenant_id = #{tenantId} AND scope = #{scope} AND owner_user_id = #{ownerUserId}
      ORDER BY id ASC
      """)
  List<EmbeddingBindingEntity> selectByTenantScopeOwner(
      @Param("tenantId") Long tenantId,
      @Param("scope") String scope,
      @Param("ownerUserId") Long ownerUserId);

  @Select(
      """
      SELECT id,tenant_id,scope,owner_user_id,ai_model_id,revision,set_by,set_at,updated_at
      FROM ai_embedding_binding
      WHERE tenant_id = #{tenantId} AND scope = #{scope} AND owner_user_id = #{ownerUserId}
      ORDER BY id ASC
      FOR UPDATE
      """)
  List<EmbeddingBindingEntity> selectByTenantScopeOwnerForUpdate(
      @Param("tenantId") Long tenantId,
      @Param("scope") String scope,
      @Param("ownerUserId") Long ownerUserId);

  @Insert(
      """
      INSERT INTO ai_embedding_binding
      (tenant_id,scope,owner_user_id,ai_model_id,revision,set_by,set_at,updated_at)
      VALUES
      (#{tenantId},#{scope},#{ownerUserId},#{aiModelId},#{revision},#{setBy},#{setAt},NOW())
      """)
  int insertBinding(
      @Param("tenantId") Long tenantId,
      @Param("scope") String scope,
      @Param("ownerUserId") Long ownerUserId,
      @Param("aiModelId") Long aiModelId,
      @Param("revision") Long revision,
      @Param("setBy") Long setBy,
      @Param("setAt") LocalDateTime setAt);

  @Update(
      """
      UPDATE ai_embedding_binding
      SET ai_model_id = #{aiModelId},revision = #{revision},set_by = #{setBy},set_at = #{setAt},updated_at = NOW()
      WHERE id = #{id}
      """)
  int updateBinding(
      @Param("id") Long id,
      @Param("aiModelId") Long aiModelId,
      @Param("revision") Long revision,
      @Param("setBy") Long setBy,
      @Param("setAt") LocalDateTime setAt);

  /** Runtime row resolved from binding, model and provider join. */
  @Data
  class RuntimeModelRow {

    private Long revision;
    private Long aiModelId;
    private Long tenantId;
    private String providerModelId;
    private String modelType;
    private Integer embeddingDimension;
    private String apiKey;
    private String baseUrl;
    private String status;
    private String providerCode;
  }
}
