package com.sunny.datapillar.openlineage.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.openlineage.config.OpenLineageRuntimeConfig;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.pipeline.EmbeddingEventPublisher;
import com.sunny.datapillar.openlineage.pipeline.EmbeddingTopicPublisher;
import com.sunny.datapillar.openlineage.source.event.DeadLetterEvent;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.dto.request.DlqReplayRequest;
import com.sunny.datapillar.openlineage.web.dto.request.SetEmbeddingRequest;
import com.sunny.datapillar.openlineage.web.dto.response.DlqReplayResponse;
import com.sunny.datapillar.openlineage.web.dto.response.SetEmbeddingResponse;
import com.sunny.datapillar.openlineage.web.entity.EmbeddingBindingEntity;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Embedding service for model binding and embedding DLQ replay. */
@Service
public class EmbeddingService {

  private static final Set<String> REBUILD_ALLOWED_ROLES = Set.of("ADMIN", "OWNER");
  private static final String DW_SCOPE = "DW";
  private static final Long DW_OWNER_USER_ID = 0L;

  private final EmbeddingBindingMapper embeddingBindingMapper;
  private final EmbeddingEventPublisher embeddingEventPublisher;
  private final EmbeddingTopicPublisher embeddingTopicPublisher;
  private final OpenLineageRuntimeConfig runtimeProperties;
  private final ObjectMapper openLineageObjectMapper;

  public EmbeddingService(
      EmbeddingBindingMapper embeddingBindingMapper,
      EmbeddingEventPublisher embeddingEventPublisher,
      EmbeddingTopicPublisher embeddingTopicPublisher,
      OpenLineageRuntimeConfig runtimeProperties,
      @Qualifier("openLineageObjectMapper") ObjectMapper openLineageObjectMapper) {
    this.embeddingBindingMapper = embeddingBindingMapper;
    this.embeddingEventPublisher = embeddingEventPublisher;
    this.embeddingTopicPublisher = embeddingTopicPublisher;
    this.runtimeProperties = runtimeProperties;
    this.openLineageObjectMapper = openLineageObjectMapper;
  }

  @Transactional(rollbackFor = Exception.class)
  public SetEmbeddingResponse setEmbedding(SetEmbeddingRequest request) {
    if (request == null || request.aiModelId() == null || request.aiModelId() <= 0) {
      throw new BadRequestException("aiModelId is invalid");
    }

    TrustedIdentityContext identity = requireUserIdentity();
    Long tenantId = requireTenantId();
    String tenantCode = requireTenantCode();

    EmbeddingBindingMapper.RuntimeModelRow runtime =
        validateEmbeddingModel(tenantId, request.aiModelId());

    EmbeddingBindingEntity current = loadDwBinding(tenantId, true, false);
    LocalDateTime now = LocalDateTime.now();
    boolean modelChanged;
    Long revision;

    if (current == null) {
      int inserted =
          embeddingBindingMapper.insertBinding(
              tenantId,
              DW_SCOPE,
              DW_OWNER_USER_ID,
              request.aiModelId(),
              1L,
              identity.userId(),
              now);
      if (inserted <= 0) {
        throw new BadRequestException("setEmbedding failed");
      }
      current = loadDwBinding(tenantId, true, true);
      modelChanged = true;
      revision = current.getRevision();
    } else {
      modelChanged = !request.aiModelId().equals(current.getAiModelId());
      revision = modelChanged ? current.getRevision() + 1L : current.getRevision();
      int updated =
          embeddingBindingMapper.updateBinding(
              current.getId(), request.aiModelId(), revision, identity.userId(), now);
      if (updated <= 0) {
        throw new BadRequestException("setEmbedding failed");
      }
      current.setAiModelId(request.aiModelId());
      current.setRevision(revision);
      current.setSetBy(identity.userId());
      current.setSetAt(now);
    }

    if (modelChanged) {
      Tenant tenant = new Tenant();
      tenant.setTenantId(tenantId);
      tenant.setTenantCode(tenantCode);
      embeddingEventPublisher.enqueueTenantRefresh(
          tenant, runtime, revision, EmbeddingTriggerType.MODEL_SWITCH);
    }

    return new SetEmbeddingResponse(
        current.getTenantId(),
        current.getScope(),
        current.getAiModelId(),
        current.getRevision(),
        current.getSetBy(),
        current.getSetAt());
  }

  public DlqReplayResponse replayEmbeddingDlq(DlqReplayRequest request) {
    if (request == null) {
      throw new BadRequestException("replay payload is missing");
    }
    if (!hasText(request.payload())) {
      throw new BadRequestException("payload is empty");
    }

    TrustedIdentityContext identity = requireIdentity();
    Long tenantId = requireTenantId();
    requireRebuildPermission(identity);
    String tenantCode = requireTenantCode();
    int resetAttempt = request.attempt() == null ? 0 : Math.max(0, request.attempt());

    try {
      DeadLetterEvent deadLetterEvent =
          openLineageObjectMapper.readValue(request.payload(), DeadLetterEvent.class);
      Map<String, String> headers =
          deadLetterEvent.getOriginalHeaders() == null
              ? new LinkedHashMap<>()
              : new LinkedHashMap<>(deadLetterEvent.getOriginalHeaders());
      validateReplayTenant(tenantId, headers.get(EventHeaders.TENANT_ID));
      validateReplayTenantCode(tenantCode, headers.get(EventHeaders.TENANT_CODE));
      if (!hasText(deadLetterEvent.getOriginalBody())) {
        throw new BadRequestException("DLQ replay body is empty");
      }
      if (!hasText(headers.get(EventHeaders.MESSAGE_ID))) {
        headers.put(EventHeaders.MESSAGE_ID, UUID.randomUUID().toString());
      }
      headers.put(EventHeaders.ATTEMPT, String.valueOf(resetAttempt));
      headers.put(EventHeaders.ENQUEUED_AT, String.valueOf(System.currentTimeMillis()));
      headers.put(EventHeaders.SCHEMA_VERSION, EventHeaders.EMBEDDING_SCHEMA);

      String replayKey = tenantId + "|" + headers.get(EventHeaders.MESSAGE_ID);
      embeddingTopicPublisher.send(deadLetterEvent.getOriginalBody(), replayKey, headers);

      return new DlqReplayResponse(
          "accepted",
          tenantId,
          runtimeProperties.getMq().getTopic().getEmbeddingDlq(),
          runtimeProperties.getMq().getTopic().getEmbedding(),
          headers.get(EventHeaders.MESSAGE_ID));
    } catch (JsonProcessingException ex) {
      throw new BadRequestException(ex, "DLQ replay payload is invalid");
    }
  }

  private EmbeddingBindingMapper.RuntimeModelRow validateEmbeddingModel(
      Long tenantId, Long aiModelId) {
    EmbeddingBindingMapper.RuntimeModelRow row =
        embeddingBindingMapper.selectModelRuntimeById(tenantId, aiModelId);
    if (row == null) {
      throw new NotFoundException("Model does not exist: %s", aiModelId);
    }
    if (!"embeddings".equalsIgnoreCase(row.getModelType())) {
      throw new BadRequestException("Model type must be embeddings");
    }
    if (!"ACTIVE".equalsIgnoreCase(row.getStatus())) {
      throw new BadRequestException("Model is not active");
    }
    if (!hasText(row.getProviderCode())) {
      throw new BadRequestException("Model providerCode is empty");
    }
    if (!hasText(row.getProviderModelId())) {
      throw new BadRequestException("Model providerModelId is empty");
    }
    if (!hasText(row.getApiKey())) {
      throw new BadRequestException("Model apiKey is empty");
    }
    return row;
  }

  private EmbeddingBindingEntity loadDwBinding(Long tenantId, boolean forUpdate, boolean required) {
    List<EmbeddingBindingEntity> rows =
        forUpdate
            ? embeddingBindingMapper.selectByTenantScopeOwnerForUpdate(
                tenantId, DW_SCOPE, DW_OWNER_USER_ID)
            : embeddingBindingMapper.selectByTenantScopeOwner(tenantId, DW_SCOPE, DW_OWNER_USER_ID);
    if (rows == null || rows.isEmpty()) {
      if (required) {
        throw new BadRequestException("DW embedding model is not configured");
      }
      return null;
    }
    if (rows.size() > 1) {
      throw new BadRequestException(
          "DW embedding binding duplicated: tenantId=%s scope=%s ownerUserId=%s",
          tenantId, DW_SCOPE, DW_OWNER_USER_ID);
    }
    return rows.getFirst();
  }

  private void validateReplayTenant(Long expectedTenantId, String payloadTenantId) {
    if (!hasText(payloadTenantId)) {
      throw new ForbiddenException("tenantId access denied");
    }
    try {
      long value = Long.parseLong(payloadTenantId);
      if (!Long.valueOf(value).equals(expectedTenantId)) {
        throw new ForbiddenException("tenantId access denied");
      }
    } catch (NumberFormatException ex) {
      throw new ForbiddenException("tenantId access denied");
    }
  }

  private boolean hasText(String value) {
    return StringUtils.hasText(value);
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
    if (!hasText(tenantCode)) {
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

  private void validateReplayTenantCode(String expectedTenantCode, String payloadTenantCode) {
    if (!hasText(payloadTenantCode) || !expectedTenantCode.equals(payloadTenantCode.trim())) {
      throw new ForbiddenException("tenantCode access denied");
    }
  }
}
