package com.sunny.datapillar.openlineage.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.openlineage.pipeline.RebuildCommandTopicPublisher;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.source.event.RebuildCommand;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.dto.request.RebuildRequest;
import com.sunny.datapillar.openlineage.web.dto.response.RebuildResponse;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Rebuild service for tenant full rebuild flow. */
@Service
public class RebuildService {

  private static final Set<String> REBUILD_ALLOWED_ROLES = Set.of("ADMIN", "OWNER");
  private static final String DW_SCOPE = "DW";
  private static final Long DW_OWNER_USER_ID = 0L;

  private final EmbeddingBindingMapper embeddingBindingMapper;
  private final RebuildCommandTopicPublisher rebuildCommandTopicPublisher;
  private final ObjectMapper openLineageObjectMapper;

  public RebuildService(
      EmbeddingBindingMapper embeddingBindingMapper,
      RebuildCommandTopicPublisher rebuildCommandTopicPublisher,
      @Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper) {
    this.embeddingBindingMapper = embeddingBindingMapper;
    this.rebuildCommandTopicPublisher = rebuildCommandTopicPublisher;
    this.openLineageObjectMapper = openLineageObjectMapper;
  }

  public RebuildResponse rebuild(RebuildRequest request) {
    if (request == null) {
      throw new BadRequestException("rebuild payload is missing");
    }

    TrustedIdentityContext identity = requireUserIdentity();
    Long tenantId = requireTenantId();
    String tenantCode = requireTenantCode();
    requireRebuildPermission(identity);

    EmbeddingBindingMapper.RuntimeModelRow runtime = loadDwRuntime(tenantId);
    String rebuildId = UUID.randomUUID().toString();

    RebuildCommand command = new RebuildCommand();
    command.setRebuildId(rebuildId);
    command.setTenantId(tenantId);
    command.setTenantCode(tenantCode);
    command.setAiModelId(runtime.getAiModelId());
    command.setBindingRevision(runtime.getRevision());
    command.setProviderCode(runtime.getProviderCode());
    command.setProviderModelId(runtime.getProviderModelId());
    command.setEmbeddingDimension(runtime.getEmbeddingDimension());
    command.setBaseUrl(runtime.getBaseUrl());
    command.setApiKeyCiphertext(runtime.getApiKey());
    command.setRequestedBy(identity.userId());
    command.setRequestedAt(Instant.now().toEpochMilli());

    long now = System.currentTimeMillis();
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(EventHeaders.MESSAGE_ID, rebuildId);
    headers.put(EventHeaders.REBUILD_ID, rebuildId);
    headers.put(EventHeaders.TENANT_ID, String.valueOf(tenantId));
    headers.put(EventHeaders.TENANT_CODE, tenantCode);
    headers.put(EventHeaders.SOURCE, "rebuild-api");
    headers.put(EventHeaders.TRIGGER, "REBUILD");
    headers.put(EventHeaders.ATTEMPT, "0");
    headers.put(EventHeaders.ENQUEUED_AT, String.valueOf(now));
    headers.put(EventHeaders.SCHEMA_VERSION, EventHeaders.REBUILD_COMMAND_SCHEMA);

    String messageKey = String.valueOf(tenantId);
    rebuildCommandTopicPublisher.send(toJson(command), messageKey, headers);

    return new RebuildResponse(
        "accepted", tenantId, runtime.getAiModelId(), runtime.getRevision(), 0, 0);
  }

  private EmbeddingBindingMapper.RuntimeModelRow loadDwRuntime(Long tenantId) {
    List<EmbeddingBindingMapper.RuntimeModelRow> rows =
        embeddingBindingMapper.selectDwRuntimeByTenant(tenantId, DW_SCOPE, DW_OWNER_USER_ID);
    if (rows == null || rows.isEmpty()) {
      throw new BadRequestException("DW embedding model is not configured");
    }
    if (rows.size() > 1) {
      throw new BadRequestException(
          "DW embedding binding duplicated: tenantId=%s scope=%s ownerUserId=%s",
          tenantId, DW_SCOPE, DW_OWNER_USER_ID);
    }
    EmbeddingBindingMapper.RuntimeModelRow row = rows.getFirst();
    validateRuntime(row);
    return row;
  }

  private void validateRuntime(EmbeddingBindingMapper.RuntimeModelRow row) {
    if (row.getRevision() == null || row.getRevision() <= 0) {
      throw new BadRequestException("DW embedding binding revision is invalid");
    }
    if (!"embeddings".equalsIgnoreCase(row.getModelType())) {
      throw new BadRequestException("Model type must be embeddings");
    }
    if (!"ACTIVE".equalsIgnoreCase(row.getStatus())) {
      throw new BadRequestException("Model is not active");
    }
    if (row.getAiModelId() == null || row.getAiModelId() <= 0) {
      throw new BadRequestException("Model aiModelId is invalid");
    }
    if (row.getProviderCode() == null || row.getProviderCode().isBlank()) {
      throw new BadRequestException("Model providerCode is empty");
    }
    if (row.getProviderModelId() == null || row.getProviderModelId().isBlank()) {
      throw new BadRequestException("Model providerModelId is empty");
    }
    if (row.getApiKey() == null || row.getApiKey().isBlank()) {
      throw new BadRequestException("Model apiKey is empty");
    }
  }

  private Long requireTenantId() {
    Long tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null || tenantId <= 0) {
      throw new UnauthorizedException("trusted_identity_tenant_id_missing");
    }
    return tenantId;
  }

  private String requireTenantCode() {
    String tenantCode = TenantContextHolder.getTenantCode();
    if (tenantCode == null || tenantCode.isBlank()) {
      throw new UnauthorizedException("trusted_identity_tenant_code_missing");
    }
    return tenantCode.trim();
  }

  private TrustedIdentityContext requireIdentity() {
    TrustedIdentityContext identity = TrustedIdentityContextHolder.get();
    if (identity == null || identity.principalType() == null || identity.tenantId() == null) {
      throw new UnauthorizedException("trusted_identity_context_missing");
    }
    return identity;
  }

  private TrustedIdentityContext requireUserIdentity() {
    TrustedIdentityContext identity = requireIdentity();
    if (identity.principalType() != PrincipalType.USER || identity.userId() == null) {
      throw new UnauthorizedException("trusted_user_context_missing");
    }
    return identity;
  }

  private void requireRebuildPermission(TrustedIdentityContext identity) {
    if (identity.roles().stream().noneMatch(REBUILD_ALLOWED_ROLES::contains)) {
      throw new ForbiddenException("No rebuild permission");
    }
  }

  private String toJson(RebuildCommand command) {
    try {
      return openLineageObjectMapper.writeValueAsString(command);
    } catch (JsonProcessingException ex) {
      throw new InternalException(ex, "Serialize rebuild command failed");
    }
  }
}
