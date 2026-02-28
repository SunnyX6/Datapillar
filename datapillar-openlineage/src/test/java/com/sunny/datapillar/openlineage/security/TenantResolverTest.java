package com.sunny.datapillar.openlineage.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.openlineage.exception.OpenLineageTenantMismatchException;
import com.sunny.datapillar.openlineage.model.OpenLineageEventEnvelope;
import com.sunny.datapillar.openlineage.model.TenantSourceType;
import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineageClientUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TenantResolverTest {

    private final TenantResolver resolver = new TenantResolver();
    private final ObjectMapper mapper = OpenLineageClientUtils.newObjectMapper();

    @Test
    void shouldResolveGravitinoTenantFromFacet() throws Exception {
        OpenLineageEventEnvelope envelope = parseRunEvent("""
                {
                  "eventTime": "2026-02-28T00:00:00Z",
                  "eventType": "START",
                  "producer": "https://gravitino.apache.org",
                  "run": {"runId": "11111111-1111-1111-1111-111111111111"},
                  "job": {"namespace": "gravitino://tenant/1001", "name": "gravitino.table.create"},
                  "inputs": [{
                    "namespace": "gravitino://tenant/1001/OneMeta/OneDS",
                    "name": "sales.orders",
                    "facets": {"gravitino": {"tenantId": 1001, "tenantCode": "t-1001", "tenantName": "Tenant1001"}}
                  }],
                  "outputs": []
                }
                """);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openlineage");
        GatewayAssertionContext.attach(request, new GatewayAssertionContext(
                1L,
                1001L,
                "t-1001",
                "Tenant1001",
                "sunny",
                "sunny@qq.com",
                java.util.List.of("ADMIN"),
                false,
                null,
                null,
                "token-1"));

        TenantContext tenantContext = resolver.resolve(envelope, request);
        Assertions.assertEquals(1001L, tenantContext.tenantId());
        Assertions.assertEquals("t-1001", tenantContext.tenantCode());
        Assertions.assertEquals("Tenant1001", tenantContext.tenantName());
        Assertions.assertEquals(TenantSourceType.GRAVITINO, tenantContext.sourceType());
    }

    @Test
    void shouldResolveComputeTenantFromAssertion() throws Exception {
        OpenLineageEventEnvelope envelope = parseRunEvent("""
                {
                  "eventTime": "2026-02-28T00:00:00Z",
                  "eventType": "START",
                  "producer": "https://spark.apache.org",
                  "run": {"runId": "22222222-2222-2222-2222-222222222222"},
                  "job": {"namespace": "spark://cluster", "name": "etl.job"},
                  "inputs": [{"namespace": "spark://warehouse", "name": "dwd.orders"}],
                  "outputs": []
                }
                """);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openlineage");
        GatewayAssertionContext.attach(request, new GatewayAssertionContext(
                2L,
                2002L,
                "t-2002",
                "Tenant2002",
                "worker",
                "worker@datapillar.ai",
                java.util.List.of("DEVELOPER"),
                false,
                null,
                null,
                "token-2"));

        TenantContext tenantContext = resolver.resolve(envelope, request);
        Assertions.assertEquals(2002L, tenantContext.tenantId());
        Assertions.assertEquals("t-2002", tenantContext.tenantCode());
        Assertions.assertEquals("Tenant2002", tenantContext.tenantName());
        Assertions.assertEquals(TenantSourceType.COMPUTE_ENGINE, tenantContext.sourceType());
    }

    @Test
    void shouldRejectTenantMismatch() throws Exception {
        OpenLineageEventEnvelope envelope = parseRunEvent("""
                {
                  "eventTime": "2026-02-28T00:00:00Z",
                  "eventType": "START",
                  "producer": "https://gravitino.apache.org",
                  "run": {"runId": "33333333-3333-3333-3333-333333333333"},
                  "job": {"namespace": "gravitino://tenant/3003", "name": "gravitino.table.alter"},
                  "inputs": [{
                    "namespace": "gravitino://tenant/3003/OneMeta/OneDS",
                    "name": "sales.orders",
                    "facets": {"gravitino": {"tenantId": 3003, "tenantCode": "t-3003", "tenantName": "Tenant3003"}}
                  }],
                  "outputs": []
                }
                """);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openlineage");
        GatewayAssertionContext.attach(request, new GatewayAssertionContext(
                3L,
                9999L,
                "t-9999",
                "Tenant9999",
                "sunny",
                "sunny@qq.com",
                java.util.List.of("ADMIN"),
                false,
                null,
                null,
                "token-3"));

        Assertions.assertThrows(OpenLineageTenantMismatchException.class, () -> resolver.resolve(envelope, request));
    }

    private OpenLineageEventEnvelope parseRunEvent(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        OpenLineage.RunEvent event = mapper.treeToValue(node, OpenLineage.RunEvent.class);
        return OpenLineageEventEnvelope.fromRunEvent(event, node);
    }
}
