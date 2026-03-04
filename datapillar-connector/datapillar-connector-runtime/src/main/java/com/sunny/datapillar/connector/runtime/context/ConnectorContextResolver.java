package com.sunny.datapillar.connector.runtime.context;

import com.sunny.datapillar.connector.spi.ConnectorContext;

/** Resolves trusted connector context from request/security chain. */
public interface ConnectorContextResolver {

  ConnectorContext resolve();
}
