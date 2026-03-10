package com.sunny.datapillar.openlineage.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.openlineage.config.OpenLineageRuntimeConfig;
import com.sunny.datapillar.openlineage.pipeline.EmbeddingEventPublisher;
import com.sunny.datapillar.openlineage.pipeline.EmbeddingTopicPublisher;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.web.context.TenantContext;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.dto.request.SetEmbeddingRequest;
import com.sunny.datapillar.openlineage.web.dto.response.SetEmbeddingResponse;
import com.sunny.datapillar.openlineage.web.entity.EmbeddingBindingEntity;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

  @Mock private EmbeddingBindingMapper embeddingBindingMapper;
  @Mock private EmbeddingEventPublisher embeddingEventPublisher;
  @Mock private EmbeddingTopicPublisher embeddingTopicPublisher;

  @AfterEach
  void clearContext() {
    TenantContextHolder.clear();
    TrustedIdentityContextHolder.clear();
  }

  @Test
  void setEmbedding_shouldKeepRevisionAndNotTriggerRefreshWhenModelUnchanged() {
    EmbeddingService service = createService();
    setRequestContext();

    EmbeddingBindingEntity binding = new EmbeddingBindingEntity();
    binding.setId(1L);
    binding.setTenantId(3003L);
    binding.setScope("DW");
    binding.setAiModelId(10L);
    binding.setRevision(3L);
    binding.setSetBy(101L);
    binding.setSetAt(LocalDateTime.now().minusHours(1));

    when(embeddingBindingMapper.selectModelRuntimeById(3003L, 10L)).thenReturn(validModelRuntime());
    when(embeddingBindingMapper.selectByTenantScopeOwnerForUpdate(3003L, "DW", 0L))
        .thenReturn(List.of(binding));
    when(embeddingBindingMapper.updateBinding(eq(1L), eq(10L), eq(3L), eq(101L), any()))
        .thenReturn(1);

    SetEmbeddingResponse response = service.setEmbedding(new SetEmbeddingRequest(10L));

    assertEquals(3003L, response.tenantId());
    assertEquals("DW", response.scope());
    assertEquals(10L, response.aiModelId());
    assertEquals(3L, response.revision());
    verify(embeddingEventPublisher, never()).enqueueTenantRefresh(any(), any(), any(), any());
  }

  @Test
  void setEmbedding_shouldTriggerRefreshWhenModelChanged() {
    EmbeddingService service = createService();
    setRequestContext();

    EmbeddingBindingEntity binding = new EmbeddingBindingEntity();
    binding.setId(1L);
    binding.setTenantId(3003L);
    binding.setScope("DW");
    binding.setAiModelId(9L);
    binding.setRevision(3L);
    binding.setSetBy(101L);
    binding.setSetAt(LocalDateTime.now().minusHours(1));

    EmbeddingBindingMapper.RuntimeModelRow runtime = validModelRuntime();
    when(embeddingBindingMapper.selectModelRuntimeById(3003L, 10L)).thenReturn(runtime);
    when(embeddingBindingMapper.selectByTenantScopeOwnerForUpdate(3003L, "DW", 0L))
        .thenReturn(List.of(binding));
    when(embeddingBindingMapper.updateBinding(eq(1L), eq(10L), eq(4L), eq(101L), any()))
        .thenReturn(1);

    SetEmbeddingResponse response = service.setEmbedding(new SetEmbeddingRequest(10L));

    assertEquals(10L, response.aiModelId());
    assertEquals(4L, response.revision());
    verify(embeddingEventPublisher)
        .enqueueTenantRefresh(
            argThat(
                tenant ->
                    tenant != null
                        && Long.valueOf(3003L).equals(tenant.getTenantId())
                        && "t-3003".equals(tenant.getTenantCode())),
            eq(runtime),
            eq(4L),
            eq(EmbeddingTriggerType.MODEL_SWITCH));
  }

  @Test
  void setEmbedding_shouldRejectApiKeyPrincipal() {
    EmbeddingService service = createService();
    setApiKeyRequestContext();

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class, () -> service.setEmbedding(new SetEmbeddingRequest(10L)));

    assertEquals("trusted_user_context_missing", exception.getMessage());
  }

  private EmbeddingService createService() {
    return new EmbeddingService(
        embeddingBindingMapper,
        embeddingEventPublisher,
        embeddingTopicPublisher,
        new OpenLineageRuntimeConfig(),
        new ObjectMapper());
  }

  private void setRequestContext() {
    TenantContextHolder.set(new TenantContext(3003L, "t-3003"));
    TrustedIdentityContextHolder.set(
        new TrustedIdentityContext(
            PrincipalType.USER,
            "user:101",
            101L,
            3003L,
            "t-3003",
            "sunny",
            "sunny@datapillar.ai",
            List.of("ADMIN"),
            false,
            null,
            null,
            "https://issuer",
            "subject",
            "trace-3003"));
  }

  private void setApiKeyRequestContext() {
    TenantContextHolder.set(new TenantContext(3003L, "t-3003"));
    TrustedIdentityContextHolder.set(
        new TrustedIdentityContext(
            PrincipalType.API_KEY,
            "api-key:301",
            null,
            3003L,
            "t-3003",
            "lineage-ingest",
            null,
            List.of("ADMIN"),
            false,
            null,
            null,
            "https://issuer",
            "api-key:301",
            "trace-3003"));
  }

  private EmbeddingBindingMapper.RuntimeModelRow validModelRuntime() {
    EmbeddingBindingMapper.RuntimeModelRow runtime = new EmbeddingBindingMapper.RuntimeModelRow();
    runtime.setAiModelId(10L);
    runtime.setTenantId(3003L);
    runtime.setProviderModelId("text-embedding-3-small");
    runtime.setModelType("embeddings");
    runtime.setEmbeddingDimension(1536);
    runtime.setApiKey("cipher-api-key");
    runtime.setBaseUrl("https://api.openai.com/v1");
    runtime.setStatus("ACTIVE");
    runtime.setProviderCode("OPENAI");
    return runtime;
  }
}
