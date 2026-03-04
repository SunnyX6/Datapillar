package com.sunny.datapillar.connector.spi;

import java.util.Collections;
import java.util.Map;

/** Connector capability manifest. */
public record ConnectorManifest(
    String connectorId, String version, Map<String, OperationSpec> operations) {

  public ConnectorManifest {
    if (connectorId == null || connectorId.isBlank()) {
      throw new IllegalArgumentException("Connector id must not be blank");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("Connector version must not be blank");
    }
    operations =
        operations == null ? Collections.emptyMap() : Collections.unmodifiableMap(operations);
  }

  public boolean supports(String operationId) {
    return operations.containsKey(operationId);
  }
}
