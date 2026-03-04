package com.sunny.datapillar.connector.runtime.observe;

import com.sunny.datapillar.connector.spi.ConnectorInvocation;

/** Connector audit logger abstraction. */
public interface ConnectorAuditLogger {

  void logSuccess(ConnectorInvocation invocation, long elapsedMillis);

  void logFailure(ConnectorInvocation invocation, long elapsedMillis, Throwable throwable);
}
