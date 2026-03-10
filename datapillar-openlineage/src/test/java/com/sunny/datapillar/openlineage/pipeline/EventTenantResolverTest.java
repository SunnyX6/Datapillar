package com.sunny.datapillar.openlineage.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.openlineage.exception.OpenLineageTenantMismatchException;
import com.sunny.datapillar.openlineage.exception.OpenLineageValidationException;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.source.event.OpenLineageEvent;
import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineageClientUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EventTenantResolverTest {

  private final EventTenantResolver resolver = new EventTenantResolver();
  private final ObjectMapper mapper = OpenLineageClientUtils.newObjectMapper();

  @Test
  void shouldResolveGravitinoTenantFromHeaderAndFacet() throws Exception {
    OpenLineageEvent event =
        parseRunEvent(
            """
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

    Tenant tenant = resolver.resolve(1001L, "t-1001", event);
    Assertions.assertEquals(1001L, tenant.getTenantId());
    Assertions.assertEquals("t-1001", tenant.getTenantCode());
    Assertions.assertEquals("Tenant1001", tenant.getTenantName());
  }

  @Test
  void shouldResolveComputeTenantFromHeaderWhenFacetMissing() throws Exception {
    OpenLineageEvent event =
        parseRunEvent(
            """
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

    Tenant tenant = resolver.resolve(2002L, "t-2002", event);
    Assertions.assertEquals(2002L, tenant.getTenantId());
    Assertions.assertEquals("t-2002", tenant.getTenantCode());
    Assertions.assertEquals("t-2002", tenant.getTenantName());
  }

  @Test
  void shouldRejectTenantIdMismatch() throws Exception {
    OpenLineageEvent event =
        parseRunEvent(
            """
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

    Assertions.assertThrows(
        OpenLineageTenantMismatchException.class, () -> resolver.resolve(9999L, "t-9999", event));
  }

  @Test
  void shouldRejectTenantCodeMismatch() throws Exception {
    OpenLineageEvent event =
        parseRunEvent(
            """
                {
                  "eventTime": "2026-02-28T00:00:00Z",
                  "eventType": "START",
                  "producer": "https://gravitino.apache.org",
                  "run": {"runId": "44444444-4444-4444-4444-444444444444"},
                  "job": {"namespace": "gravitino://tenant/4004", "name": "gravitino.table.create"},
                  "inputs": [{
                    "namespace": "gravitino://tenant/4004/OneMeta/OneDS",
                    "name": "sales.orders",
                    "facets": {"gravitino": {"tenantId": 4004, "tenantCode": "t-9999", "tenantName": "Tenant4004"}}
                  }],
                  "outputs": []
                }
                """);

    Assertions.assertThrows(
        OpenLineageTenantMismatchException.class, () -> resolver.resolve(4004L, "t-4004", event));
  }

  @Test
  void shouldRejectInvalidHeaderTenantId() throws Exception {
    OpenLineageEvent event =
        parseRunEvent(
            """
                {
                  "eventTime": "2026-02-28T00:00:00Z",
                  "eventType": "START",
                  "producer": "https://spark.apache.org",
                  "run": {"runId": "66666666-6666-6666-6666-666666666666"},
                  "job": {"namespace": "spark://cluster", "name": "etl.job"},
                  "inputs": [{"namespace": "spark://warehouse", "name": "dwd.orders"}],
                  "outputs": []
                }
                """);

    Assertions.assertThrows(
        OpenLineageValidationException.class, () -> resolver.resolve(null, "t-6006", event));
  }

  private OpenLineageEvent parseRunEvent(String json) throws Exception {
    JsonNode node = mapper.readTree(json);
    OpenLineage.RunEvent event = mapper.treeToValue(node, OpenLineage.RunEvent.class);
    return OpenLineageEvent.fromRunEvent(event, node);
  }
}
