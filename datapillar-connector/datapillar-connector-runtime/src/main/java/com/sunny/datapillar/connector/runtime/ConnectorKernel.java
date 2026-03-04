package com.sunny.datapillar.connector.runtime;

import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.ConnectorResponse;

/** Runtime invocation kernel for all connector calls. */
public interface ConnectorKernel {

  ConnectorResponse invoke(ConnectorInvocation invocation);
}
