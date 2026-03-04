package com.sunny.datapillar.connector.spi;

/** Unified connector invocation contract. */
public interface Connector {

  ConnectorManifest manifest();

  ConnectorResponse invoke(ConnectorInvocation invocation);

  default void initialize() {}

  default void destroy() {}
}
