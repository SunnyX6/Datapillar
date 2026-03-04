package com.sunny.datapillar.connector.runtime.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ConnectorFactoryLoaderTest {

  @Test
  void discoverFactories_shouldLoadServiceProvider() {
    ConnectorFactoryLoader loader = new ConnectorFactoryLoader();

    var factories = loader.discoverFactories();
    var identifiers =
        factories.stream()
            .map(factory -> factory.connectorIdentifier())
            .collect(Collectors.toSet());

    assertTrue(identifiers.contains("loader-test"));
  }
}
