package com.sunny.datapillar.connector.airflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunny.datapillar.connector.airflow.transport.http.AirflowHttpClient;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ErrorType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WorkflowServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deploy_shouldBuildTenantScopedDagId() {
    AirflowHttpClient httpClient = Mockito.mock(AirflowHttpClient.class);
    WorkflowService workflowService = new WorkflowService(httpClient, objectMapper);
    when(httpClient.post(eq("/dags"), any(), eq(Map.of()), any()))
        .thenReturn(objectMapper.createObjectNode().put("success", true));

    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("workflowId", 123);
    payload.set("workflow", objectMapper.createObjectNode().put("name", "wf-123"));
    ConnectorContext context =
        new ConnectorContext(1L, "tenant_acme", 2L, "sunny", null, null, null, false, null, null);

    workflowService.deploy(payload, context);

    ArgumentCaptor<ObjectNode> bodyCaptor = ArgumentCaptor.forClass(ObjectNode.class);
    verify(httpClient).post(eq("/dags"), bodyCaptor.capture(), eq(Map.of()), eq(context));
    assertEquals(123L, bodyCaptor.getValue().path("workflow_id").asLong());
    assertEquals("dp_tenant_acme_w123", bodyCaptor.getValue().path("dag_id").asText());
  }

  @Test
  void deploy_shouldRejectWhenTenantCodeMissing() {
    AirflowHttpClient httpClient = Mockito.mock(AirflowHttpClient.class);
    WorkflowService workflowService = new WorkflowService(httpClient, objectMapper);
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("workflowId", 1);
    payload.set("workflow", objectMapper.createObjectNode());
    ConnectorContext context =
        new ConnectorContext(1L, null, 2L, "sunny", null, null, null, false, null, null);

    ConnectorException exception =
        assertThrows(ConnectorException.class, () -> workflowService.deploy(payload, context));

    assertEquals(ErrorType.BAD_REQUEST, exception.errorType());
    assertEquals("Tenant code is required for airflow operations", exception.getMessage());
  }

  @Test
  void deploy_shouldRejectWhenTenantCodeInvalid() {
    AirflowHttpClient httpClient = Mockito.mock(AirflowHttpClient.class);
    WorkflowService workflowService = new WorkflowService(httpClient, objectMapper);
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("workflowId", 1);
    payload.set("workflow", objectMapper.createObjectNode());
    ConnectorContext context =
        new ConnectorContext(1L, "INVALID CODE", 2L, "sunny", null, null, null, false, null, null);

    ConnectorException exception =
        assertThrows(ConnectorException.class, () -> workflowService.deploy(payload, context));

    assertEquals(ErrorType.BAD_REQUEST, exception.errorType());
    assertEquals("Tenant code format is invalid: invalid code", exception.getMessage());
  }
}
