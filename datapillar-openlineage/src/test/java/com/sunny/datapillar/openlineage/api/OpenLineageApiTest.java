package com.sunny.datapillar.openlineage.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.openlineage.exception.OpenLineageValidationException;
import com.sunny.datapillar.openlineage.security.TenantContext;
import com.sunny.datapillar.openlineage.security.TenantResolver;
import com.sunny.datapillar.openlineage.service.OpenLineageService;
import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineageClientUtils;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenLineageApiTest {

    @Mock
    private OpenLineageService openLineageService;

    @Mock
    private TenantResolver tenantResolver;

    private final ObjectMapper mapper = OpenLineageClientUtils.newObjectMapper();

    @Test
    void shouldReturnCreatedWhenServiceSucceeds() throws Exception {
        OpenLineageApi api = new OpenLineageApi(mapper, openLineageService, tenantResolver);

        JsonNode payload = mapper.readTree("""
                {
                  "eventTime": "2026-02-28T00:00:00Z",
                  "eventType": "START",
                  "producer": "https://spark.apache.org",
                  "run": {"runId": "11111111-1111-1111-1111-111111111111"},
                  "job": {"namespace": "spark://cluster", "name": "etl.job"},
                  "inputs": [{"namespace": "spark://warehouse", "name": "dwd.orders"}],
                  "outputs": []
                }
                """);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openlineage");
        TenantContext tenantContext = new TenantContext(2002L, "t-2002", "Tenant2002", com.sunny.datapillar.openlineage.model.TenantSourceType.COMPUTE_ENGINE);
        when(tenantResolver.resolve(any(), any())).thenReturn(tenantContext);
        when(openLineageService.createAsync(any(OpenLineage.RunEvent.class), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        ResponseEntity<com.sunny.datapillar.openlineage.api.dto.IngestAckResponse> response =
                api.ingest(payload, request).join();

        Assertions.assertEquals(201, response.getStatusCode().value());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals("accepted", response.getBody().status());
    }

    @Test
    void shouldReturnBadRequestWhenServiceFailsWithValidationError() throws Exception {
        OpenLineageApi api = new OpenLineageApi(mapper, openLineageService, tenantResolver);

        JsonNode payload = mapper.readTree("""
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
        TenantContext tenantContext = new TenantContext(2002L, "t-2002", "Tenant2002", com.sunny.datapillar.openlineage.model.TenantSourceType.COMPUTE_ENGINE);
        when(tenantResolver.resolve(any(), any())).thenReturn(tenantContext);
        when(openLineageService.createAsync(any(OpenLineage.RunEvent.class), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new OpenLineageValidationException("bad request")));

        ResponseEntity<com.sunny.datapillar.openlineage.api.dto.IngestAckResponse> response =
                api.ingest(payload, request).join();

        Assertions.assertEquals(400, response.getStatusCode().value());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals("bad_request", response.getBody().status());
    }
}
