package com.sunny.datapillar.connector.runtime.observe;

import com.sunny.datapillar.connector.spi.ConnectorInvocation;

/** Connector metric recorder abstraction. */
public interface ConnectorMetricsRecorder {

  void recordSuccess(ConnectorInvocation invocation, long elapsedMillis);

  void recordFailure(ConnectorInvocation invocation, long elapsedMillis, Throwable throwable);
}
