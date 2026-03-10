package com.sunny.datapillar.openlineage.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.openlineage.pipeline.RebuildCommandTopicPublisher;
import com.sunny.datapillar.openlineage.source.event.EventHeaders;
import com.sunny.datapillar.openlineage.source.event.RebuildCommand;
import com.sunny.datapillar.openlineage.web.context.TenantContext;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.dto.request.RebuildRequest;
import com.sunny.datapillar.openlineage.web.dto.response.RebuildResponse;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class RebuildServiceTest {

  @Mock private EmbeddingBindingMapper embeddingBindingMapper;
  @Mock private RebuildCommandTopicPublisher rebuildCommandTopicPublisher;

  @AfterEach
  void clearContext() {
    TenantContextHolder.clear();
    TrustedIdentityContextHolder.clear();
  }

  @Test
  void rebuild_shouldPublishRebuildCommandMessage() throws Exception {
    RebuildService service =
        new RebuildService(
            embeddingBindingMapper, rebuildCommandTopicPublisher, new ObjectMapper());

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

    EmbeddingBindingMapper.RuntimeModelRow runtime = new EmbeddingBindingMapper.RuntimeModelRow();
    runtime.setRevision(8L);
    runtime.setAiModelId(11L);
    runtime.setTenantId(3003L);
    runtime.setModelType("embeddings");
    runtime.setStatus("ACTIVE");
    runtime.setProviderCode("OPENAI");
    runtime.setProviderModelId("text-embedding-3-small");
    runtime.setEmbeddingDimension(1536);
    runtime.setBaseUrl("https://api.openai.com/v1");
    runtime.setApiKey("ENCv1:test-key");
    when(embeddingBindingMapper.selectDwRuntimeByTenant(3003L, "DW", 0L))
        .thenReturn(List.of(runtime));

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Map<String, String>> headerCaptor = ArgumentCaptor.forClass(Map.class);

    RebuildResponse response = service.rebuild(new RebuildRequest());

    verify(rebuildCommandTopicPublisher)
        .send(bodyCaptor.capture(), keyCaptor.capture(), headerCaptor.capture());

    assertEquals("accepted", response.status());
    assertEquals(3003L, response.tenantId());
    assertEquals(11L, response.aiModelId());
    assertEquals(8L, response.revision());
    assertEquals(0, response.graphUpserts());
    assertEquals(0, response.embeddingTasks());
    assertEquals("3003", keyCaptor.getValue());

    RebuildCommand command =
        new ObjectMapper().readValue(bodyCaptor.getValue(), RebuildCommand.class);
    assertNotNull(command.getRebuildId());
    assertEquals(3003L, command.getTenantId());
    assertEquals("t-3003", command.getTenantCode());
    assertEquals(11L, command.getAiModelId());
    assertEquals(8L, command.getBindingRevision());
    assertEquals("OPENAI", command.getProviderCode());
    assertEquals("text-embedding-3-small", command.getProviderModelId());
    assertEquals(1536, command.getEmbeddingDimension());
    assertEquals("https://api.openai.com/v1", command.getBaseUrl());
    assertEquals("ENCv1:test-key", command.getApiKeyCiphertext());
    assertEquals(101L, command.getRequestedBy());
    assertNotNull(command.getRequestedAt());

    Map<String, String> headers = headerCaptor.getValue();
    assertEquals(command.getRebuildId(), headers.get(EventHeaders.MESSAGE_ID));
    assertEquals(command.getRebuildId(), headers.get(EventHeaders.REBUILD_ID));
    assertEquals("3003", headers.get(EventHeaders.TENANT_ID));
    assertEquals("t-3003", headers.get(EventHeaders.TENANT_CODE));
    assertEquals("rebuild-api", headers.get(EventHeaders.SOURCE));
    assertEquals("REBUILD", headers.get(EventHeaders.TRIGGER));
    assertEquals("0", headers.get(EventHeaders.ATTEMPT));
    assertEquals(EventHeaders.REBUILD_COMMAND_SCHEMA, headers.get(EventHeaders.SCHEMA_VERSION));
  }

  @Test
  void rebuild_shouldRejectApiKeyPrincipal() {
    RebuildService service =
        new RebuildService(
            embeddingBindingMapper, rebuildCommandTopicPublisher, new ObjectMapper());

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

    UnauthorizedException exception =
        assertThrows(UnauthorizedException.class, () -> service.rebuild(new RebuildRequest()));

    assertEquals("trusted_user_context_missing", exception.getMessage());
  }
}
