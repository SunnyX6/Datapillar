package com.sunny.datapillar.connector.runtime.bootstrap;

import com.sunny.datapillar.connector.spi.Connector;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Connector runtime registry. */
public class ConnectorRegistry {

  private final Map<String, Connector> connectors;

  public ConnectorRegistry(Map<String, Connector> connectors) {
    this.connectors =
        connectors == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(connectors));
  }

  public Connector getRequired(String connectorId) {
    Connector connector = connectors.get(connectorId);
    if (connector == null) {
      throw new IllegalStateException("Connector is not registered: " + connectorId);
    }
    return connector;
  }

  public Map<String, Connector> connectors() {
    return connectors;
  }

  public void destroyAll() {
    connectors.values().forEach(Connector::destroy);
  }
}
