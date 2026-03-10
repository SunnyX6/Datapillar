package com.sunny.datapillar.openlineage.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.openlineage.web.context.TenantContext;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.dto.response.InitialGraphResponse;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import com.sunny.datapillar.openlineage.web.mapper.KnowledgeGraphMapper;
import com.sunny.datapillar.openlineage.web.security.TenantApiKeyDecryptor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

  @Mock private EmbeddingBindingMapper embeddingBindingMapper;
  @Mock private TenantApiKeyDecryptor tenantApiKeyDecryptor;
  @Mock private KnowledgeGraphMapper knowledgeGraphMapper;

  @AfterEach
  void clearContext() {
    TenantContextHolder.clear();
    TrustedIdentityContextHolder.clear();
  }

  @Test
  void initial_shouldAllowApiKeyPrincipal() {
    QueryService service =
        new QueryService(embeddingBindingMapper, tenantApiKeyDecryptor, knowledgeGraphMapper);
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

    InitialGraphResponse initialGraphResponse =
        new InitialGraphResponse(3003L, List.of(), List.of());
    when(knowledgeGraphMapper.loadInitialGraph(3003L, 500)).thenReturn(initialGraphResponse);

    InitialGraphResponse response = service.initial(null);

    assertEquals(3003L, response.tenantId());
    verify(knowledgeGraphMapper).loadInitialGraph(3003L, 500);
  }
}
