package com.sunny.datapillar.openlineage.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.openlineage.config.OpenLineageRuntimeConfig;
import com.sunny.datapillar.openlineage.pipeline.EventTopicPublisher;
import com.sunny.datapillar.openlineage.web.context.TenantContext;
import com.sunny.datapillar.openlineage.web.context.TenantContextHolder;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContext;
import com.sunny.datapillar.openlineage.web.context.TrustedIdentityContextHolder;
import com.sunny.datapillar.openlineage.web.dto.request.EventIngestRequest;
import com.sunny.datapillar.openlineage.web.dto.response.EventIngestAckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

  @Mock private EventTopicPublisher eventTopicPublisher;

  @AfterEach
  void clearContext() {
    TenantContextHolder.clear();
    TrustedIdentityContextHolder.clear();
  }

  @Test
  void ingest_shouldAllowApiKeyPrincipal() {
    EventService service =
        new EventService(new ObjectMapper(), eventTopicPublisher, new OpenLineageRuntimeConfig());
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
            java.util.List.of("ADMIN"),
            false,
            null,
            null,
            "https://issuer",
            "api-key:301",
            "trace-3003"));

    EventIngestRequest request = new EventIngestRequest();
    request.putPayloadField("eventType", "COMPLETE");
    request.putPayloadField("producer", "https://openlineage.io/integration/flink");

    EventIngestAckResponse response = service.ingest(request);

    assertEquals("accepted", response.status());
    assertEquals(3003L, response.tenantId());
    assertEquals("COMPLETE", response.eventType());
    assertNotNull(response.messageId());
    verify(eventTopicPublisher)
        .send(
            eq(
                "{\"eventType\":\"COMPLETE\",\"producer\":\"https://openlineage.io/integration/flink\"}"),
            eq("3003|" + response.messageId()),
            anyMap());
  }
}
