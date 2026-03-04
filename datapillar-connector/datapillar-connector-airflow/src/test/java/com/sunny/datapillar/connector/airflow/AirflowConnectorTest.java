package com.sunny.datapillar.connector.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sunny.datapillar.connector.airflow.domain.WorkflowService;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.ErrorType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AirflowConnectorTest {

  @Test
  void invoke_shouldDispatchToWorkflowService() {
    WorkflowService workflowService = Mockito.mock(WorkflowService.class);
    AirflowConnector connector = new AirflowConnector(workflowService);
    when(workflowService.delete(any(), any()))
        .thenReturn(JsonNodeFactory.instance.objectNode().put("deleted", true));

    var response =
        connector.invoke(
            ConnectorInvocation.builder(AirflowConnector.CONNECTOR_ID, AirflowConnector.OP_DELETE)
                .payload(JsonNodeFactory.instance.objectNode().put("workflowId", 1))
                .build());

    verify(workflowService).delete(any(), any());
    assertEquals(true, response.payload().path("deleted").asBoolean(false));
  }

  @Test
  void invoke_shouldRejectUnsupportedOperation() {
    WorkflowService workflowService = Mockito.mock(WorkflowService.class);
    AirflowConnector connector = new AirflowConnector(workflowService);

    ConnectorException exception =
        assertThrows(
            ConnectorException.class,
            () ->
                connector.invoke(
                    ConnectorInvocation.builder(AirflowConnector.CONNECTOR_ID, "airflow.unknown")
                        .payload(JsonNodeFactory.instance.objectNode())
                        .build()));

    assertEquals(ErrorType.BAD_REQUEST, exception.errorType());
  }
}
