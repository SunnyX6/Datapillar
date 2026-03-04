package com.sunny.datapillar.connector.gravitino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sunny.datapillar.connector.gravitino.domain.metadata.CatalogService;
import com.sunny.datapillar.connector.gravitino.domain.metadata.MetalakeService;
import com.sunny.datapillar.connector.gravitino.domain.metadata.SchemaService;
import com.sunny.datapillar.connector.gravitino.domain.metadata.TableService;
import com.sunny.datapillar.connector.gravitino.domain.metadata.TagService;
import com.sunny.datapillar.connector.gravitino.domain.security.RolePrivilegeService;
import com.sunny.datapillar.connector.gravitino.domain.security.UserService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.MetricService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.ModifierService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.UnitService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.ValueDomainService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.WordRootService;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.ErrorType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GravitinoConnectorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MetalakeService metalakeService = Mockito.mock(MetalakeService.class);
  private final CatalogService catalogService = Mockito.mock(CatalogService.class);
  private final SchemaService schemaService = Mockito.mock(SchemaService.class);
  private final TableService tableService = Mockito.mock(TableService.class);
  private final TagService tagService = Mockito.mock(TagService.class);
  private final UserService userService = Mockito.mock(UserService.class);
  private final RolePrivilegeService rolePrivilegeService =
      Mockito.mock(RolePrivilegeService.class);
  private final WordRootService wordRootService = Mockito.mock(WordRootService.class);
  private final MetricService metricService = Mockito.mock(MetricService.class);
  private final UnitService unitService = Mockito.mock(UnitService.class);
  private final ModifierService modifierService = Mockito.mock(ModifierService.class);
  private final ValueDomainService valueDomainService = Mockito.mock(ValueDomainService.class);

  private final GravitinoConnector connector =
      new GravitinoConnector(
          objectMapper,
          metalakeService,
          catalogService,
          schemaService,
          tableService,
          tagService,
          userService,
          rolePrivilegeService,
          wordRootService,
          metricService,
          unitService,
          modifierService,
          valueDomainService);

  @Test
  void invoke_shouldDispatchSecuritySyncUser() {
    when(userService.syncUser(eq("sunny"), any()))
        .thenReturn(JsonNodeFactory.instance.objectNode().put("synced", true));

    var response =
        connector.invoke(
            ConnectorInvocation.builder(
                    GravitinoConnector.CONNECTOR_ID, GravitinoConnector.OP_SECURITY_SYNC_USER)
                .payload(JsonNodeFactory.instance.objectNode().put("username", "sunny"))
                .build());

    verify(userService).syncUser(eq("sunny"), any());
    assertEquals(true, response.payload().path("synced").asBoolean(false));
  }

  @Test
  void invoke_shouldDispatchMetadataProxyToCatalogService() {
    when(catalogService.request(any(), any(), any(), any(), any()))
        .thenReturn(JsonNodeFactory.instance.objectNode().put("catalog", true));

    var payload = JsonNodeFactory.instance.objectNode();
    payload.put("path", "/catalogs");
    payload.put("method", "GET");

    var response =
        connector.invoke(
            ConnectorInvocation.builder(
                    GravitinoConnector.CONNECTOR_ID, GravitinoConnector.OP_METADATA_PROXY)
                .payload(payload)
                .build());

    verify(catalogService).request(any(), any(), any(), any(), any());
    assertEquals(true, response.payload().path("catalog").asBoolean(false));
  }

  @Test
  void invoke_shouldDispatchSemanticModifierRequest() {
    when(modifierService.request(any(), any(), any(), any(), any()))
        .thenReturn(JsonNodeFactory.instance.objectNode().put("modifier", true));

    var payload = JsonNodeFactory.instance.objectNode();
    payload.put("path", "/metrics/abc/modifiers");
    payload.put("method", "GET");

    var response =
        connector.invoke(
            ConnectorInvocation.builder(
                    GravitinoConnector.CONNECTOR_ID, GravitinoConnector.OP_SEMANTIC_PROXY)
                .payload(payload)
                .build());

    verify(modifierService).request(any(), any(), any(), any(), any());
    assertEquals(true, response.payload().path("modifier").asBoolean(false));
  }

  @Test
  void invoke_shouldRejectMissingRequiredField() {
    ConnectorException exception =
        assertThrows(
            ConnectorException.class,
            () ->
                connector.invoke(
                    ConnectorInvocation.builder(
                            GravitinoConnector.CONNECTOR_ID, GravitinoConnector.OP_METALAKE_CREATE)
                        .payload(JsonNodeFactory.instance.objectNode())
                        .build()));

    assertEquals(ErrorType.BAD_REQUEST, exception.errorType());
    assertEquals("Missing required field: metalakeName", exception.getMessage());
  }

  @Test
  void invoke_shouldRejectUnknownOperation() {
    ConnectorException exception =
        assertThrows(
            ConnectorException.class,
            () ->
                connector.invoke(
                    ConnectorInvocation.builder(
                            GravitinoConnector.CONNECTOR_ID, "gravitino.unknown")
                        .payload(JsonNodeFactory.instance.objectNode())
                        .build()));

    assertEquals(ErrorType.BAD_REQUEST, exception.errorType());
  }
}
