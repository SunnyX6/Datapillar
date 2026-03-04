package com.sunny.datapillar.connector.runtime;

import com.sunny.datapillar.connector.runtime.bootstrap.ConnectorRegistry;
import com.sunny.datapillar.connector.runtime.context.ConnectorContextResolver;
import com.sunny.datapillar.connector.runtime.error.RuntimeErrorMapper;
import com.sunny.datapillar.connector.runtime.execute.RetryExecutor;
import com.sunny.datapillar.connector.runtime.execute.TimeoutExecutor;
import com.sunny.datapillar.connector.runtime.idempotency.IdempotencyGuard;
import com.sunny.datapillar.connector.runtime.observe.ConnectorAuditLogger;
import com.sunny.datapillar.connector.runtime.observe.ConnectorMetricsRecorder;
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.ConnectorResponse;
import com.sunny.datapillar.connector.spi.TimeoutDescriptor;

/** Default connector kernel runtime implementation. */
public class DefaultConnectorKernel implements ConnectorKernel {

  private final ConnectorRegistry registry;
  private final ConnectorContextResolver contextResolver;
  private final IdempotencyGuard idempotencyGuard;
  private final RetryExecutor retryExecutor;
  private final TimeoutExecutor timeoutExecutor;
  private final RuntimeErrorMapper errorMapper;
  private final ConnectorMetricsRecorder metricsRecorder;
  private final ConnectorAuditLogger auditLogger;

  public DefaultConnectorKernel(
      ConnectorRegistry registry,
      ConnectorContextResolver contextResolver,
      IdempotencyGuard idempotencyGuard,
      RetryExecutor retryExecutor,
      TimeoutExecutor timeoutExecutor,
      RuntimeErrorMapper errorMapper,
      ConnectorMetricsRecorder metricsRecorder,
      ConnectorAuditLogger auditLogger) {
    this.registry = registry;
    this.contextResolver = contextResolver;
    this.idempotencyGuard = idempotencyGuard;
    this.retryExecutor = retryExecutor;
    this.timeoutExecutor = timeoutExecutor;
    this.errorMapper = errorMapper;
    this.metricsRecorder = metricsRecorder;
    this.auditLogger = auditLogger;
  }

  @Override
  public ConnectorResponse invoke(ConnectorInvocation invocation) {
    long start = System.currentTimeMillis();
    try {
      Connector connector = registry.getRequired(invocation.connectorId());
      if (!connector.manifest().supports(invocation.operationId())) {
        throw new IllegalStateException(
            "Unsupported connector operation: %s#%s"
                .formatted(invocation.connectorId(), invocation.operationId()));
      }

      ConnectorContext context =
          invocation.context() == null ? contextResolver.resolve() : invocation.context();
      ConnectorInvocation prepared =
          new ConnectorInvocation(
              invocation.connectorId(),
              invocation.operationId(),
              invocation.payload(),
              context,
              invocation.idempotency(),
              invocation.timeout());

      TimeoutDescriptor timeout = prepared.timeout();
      ConnectorResponse response =
          idempotencyGuard.execute(
              prepared.idempotency(),
              () ->
                  timeoutExecutor.execute(
                      timeout == null ? null : timeout.timeout(),
                      () ->
                          retryExecutor.execute(
                              timeout == null ? null : timeout.maxAttempts(),
                              timeout == null ? null : timeout.backoff(),
                              () -> connector.invoke(prepared))));

      long elapsed = System.currentTimeMillis() - start;
      metricsRecorder.recordSuccess(prepared, elapsed);
      auditLogger.logSuccess(prepared, elapsed);
      return response;
    } catch (Throwable throwable) {
      long elapsed = System.currentTimeMillis() - start;
      metricsRecorder.recordFailure(invocation, elapsed, throwable);
      auditLogger.logFailure(invocation, elapsed, throwable);
      throw errorMapper.map(throwable);
    }
  }
}
