package com.sunny.datapillar.connector.spi;

import java.util.Set;

/** Factory entry for connector plugin discovery. */
public interface ConnectorFactory {

  String connectorIdentifier();

  Set<ConfigOption<?>> requiredOptions();

  Set<ConfigOption<?>> optionalOptions();

  Connector create(ConnectorFactoryContext context);
}
