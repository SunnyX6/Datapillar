package com.sunny.datapillar.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.connector.runtime.bootstrap.ConnectorRegistry;
import com.sunny.datapillar.connector.runtime.error.RuntimeErrorMapper;
import com.sunny.datapillar.connector.runtime.execute.RetryExecutor;
import com.sunny.datapillar.connector.runtime.execute.TimeoutExecutor;
import com.sunny.datapillar.connector.runtime.idempotency.IdempotencyGuard;
import com.sunny.datapillar.connector.runtime.idempotency.NoopConnectorIdempotencyStore;
import com.sunny.datapillar.connector.runtime.observe.ConnectorAuditLogger;
import com.sunny.datapillar.connector.runtime.observe.ConnectorMetricsRecorder;
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorContext;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.ConnectorManifest;
import com.sunny.datapillar.connector.spi.ConnectorResponse;
import com.sunny.datapillar.connector.spi.OperationSpec;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultConnectorKernelTest {

  private final TimeoutExecutor timeoutExecutor = new TimeoutExecutor();

  @AfterEach
  void tearDown() {
    timeoutExecutor.shutdown();
  }

  @Test
  void invoke_shouldUseResolvedContextWhenInvocationContextMissing() {
    var expectedContext =
        new ConnectorContext(1L, "tenant-a", 2L, "sunny", "sub", 2L, 1L, false, "trace-1", "req-1");
    CapturingConnector connector = new CapturingConnector();
    DefaultConnectorKernel kernel =
        createKernel(new ConnectorRegistry(Map.of("demo", connector)), () -> expectedContext);

    ConnectorResponse response =
        kernel.invoke(
            ConnectorInvocation.builder("demo", "demo.op")
                .payload(JsonNodeFactory.instance.objectNode().put("ok", true))
                .build());

    assertEquals(true, response.payload().path("ok").asBoolean(false));
    assertEquals(expectedContext, connector.lastInvocation.context());
  }

  @Test
  void invoke_shouldMapUnsupportedOperationToInternalException() {
    CapturingConnector connector = new CapturingConnector();
    DefaultConnectorKernel kernel =
        createKernel(
            new ConnectorRegistry(Map.of("demo", connector)),
            () ->
                new ConnectorContext(
                    null, "tenant-a", null, null, null, null, null, false, null, null));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                kernel.invoke(
                    ConnectorInvocation.builder("demo", "demo.unsupported")
                        .payload(JsonNodeFactory.instance.objectNode())
                        .build()));

    assertInstanceOf(InternalException.class, exception);
  }

  private DefaultConnectorKernel createKernel(
      ConnectorRegistry registry,
      com.sunny.datapillar.connector.runtime.context.ConnectorContextResolver contextResolver) {
    ConnectorMetricsRecorder metricsRecorder =
        new ConnectorMetricsRecorder() {
          @Override
          public void recordSuccess(ConnectorInvocation invocation, long elapsedMillis) {}

          @Override
          public void recordFailure(
              ConnectorInvocation invocation, long elapsedMillis, Throwable throwable) {}
        };
    ConnectorAuditLogger auditLogger =
        new ConnectorAuditLogger() {
          @Override
          public void logSuccess(ConnectorInvocation invocation, long elapsedMillis) {}

          @Override
          public void logFailure(
              ConnectorInvocation invocation, long elapsedMillis, Throwable throwable) {}
        };
    return new DefaultConnectorKernel(
        registry,
        contextResolver,
        new IdempotencyGuard(new NoopConnectorIdempotencyStore()),
        new RetryExecutor(),
        timeoutExecutor,
        new RuntimeErrorMapper(),
        metricsRecorder,
        auditLogger);
  }

  private static final class CapturingConnector implements Connector {
    private ConnectorInvocation lastInvocation;

    @Override
    public ConnectorManifest manifest() {
      return new ConnectorManifest(
          "demo", "1.0.0", Map.of("demo.op", OperationSpec.read("demo.op")));
    }

    @Override
    public ConnectorResponse invoke(ConnectorInvocation invocation) {
      this.lastInvocation = invocation;
      return ConnectorResponse.of(invocation.payload());
    }
  }
}
