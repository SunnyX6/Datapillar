package com.sunny.datapillar.connector.runtime.spring;

import com.sunny.datapillar.connector.runtime.ConnectorKernel;
import com.sunny.datapillar.connector.runtime.DefaultConnectorKernel;
import com.sunny.datapillar.connector.runtime.bootstrap.ConnectorFactoryHelper;
import com.sunny.datapillar.connector.runtime.bootstrap.ConnectorFactoryLoader;
import com.sunny.datapillar.connector.runtime.bootstrap.ConnectorRegistry;
import com.sunny.datapillar.connector.runtime.config.ConnectorInstanceProperties;
import com.sunny.datapillar.connector.runtime.config.ConnectorRuntimeProperties;
import com.sunny.datapillar.connector.runtime.context.ConnectorContextResolver;
import com.sunny.datapillar.connector.runtime.error.RuntimeErrorMapper;
import com.sunny.datapillar.connector.runtime.execute.RetryExecutor;
import com.sunny.datapillar.connector.runtime.execute.TimeoutExecutor;
import com.sunny.datapillar.connector.runtime.idempotency.ConnectorIdempotencyStore;
import com.sunny.datapillar.connector.runtime.idempotency.IdempotencyGuard;
import com.sunny.datapillar.connector.runtime.idempotency.NoopConnectorIdempotencyStore;
import com.sunny.datapillar.connector.runtime.observe.ConnectorAuditLogger;
import com.sunny.datapillar.connector.runtime.observe.ConnectorMetricsRecorder;
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorFactory;
import com.sunny.datapillar.connector.spi.ConnectorFactoryContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Connector runtime auto configuration. */
@AutoConfiguration
@EnableConfigurationProperties(ConnectorRuntimeProperties.class)
public class ConnectorRuntimeAutoConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(ConnectorRuntimeAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public ConnectorFactoryLoader connectorFactoryLoader() {
    return new ConnectorFactoryLoader();
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectorFactoryHelper connectorFactoryHelper() {
    return new ConnectorFactoryHelper();
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectorRegistry connectorRegistry(
      ConnectorFactoryLoader factoryLoader,
      ConnectorFactoryHelper factoryHelper,
      ConnectorRuntimeProperties runtimeProperties) {
    List<ConnectorFactory> factories = factoryLoader.discoverFactories();
    Map<String, ConnectorFactory> factoryMap = factoryHelper.validateUniqueIdentifier(factories);

    Map<String, Connector> connectors = new LinkedHashMap<>();
    for (Map.Entry<String, ConnectorInstanceProperties> entry :
        runtimeProperties.getInstances().entrySet()) {
      String connectorId = entry.getKey();
      ConnectorFactory factory = factoryMap.get(connectorId);
      if (factory == null) {
        throw new IllegalStateException("No connector factory found for id: " + connectorId);
      }
      Map<String, String> options =
          entry.getValue() == null ? Map.of() : entry.getValue().getOptions();
      factoryHelper.validateRequiredOptions(factory, options);
      factoryHelper.validateUnsupportedOptions(factory, options);

      Connector connector = factory.create(new ConnectorFactoryContext(connectorId, options));
      connector.initialize();
      connectors.put(connectorId, connector);
      LOG.info(
          "Connector initialized: id={}, factory={}", connectorId, factory.getClass().getName());
    }

    return new ConnectorRegistry(connectors);
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectorContextResolver connectorContextResolver() {
    return () -> null;
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectorIdempotencyStore connectorIdempotencyStore() {
    return new NoopConnectorIdempotencyStore();
  }

  @Bean
  @ConditionalOnMissingBean
  public IdempotencyGuard idempotencyGuard(ConnectorIdempotencyStore idempotencyStore) {
    return new IdempotencyGuard(idempotencyStore);
  }

  @Bean
  @ConditionalOnMissingBean
  public RetryExecutor retryExecutor() {
    return new RetryExecutor();
  }

  @Bean
  @ConditionalOnMissingBean
  public TimeoutExecutor timeoutExecutor() {
    return new TimeoutExecutor();
  }

  @Bean
  @ConditionalOnMissingBean
  public RuntimeErrorMapper runtimeErrorMapper() {
    return new RuntimeErrorMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectorMetricsRecorder connectorMetricsRecorder() {
    return new ConnectorMetricsRecorder() {
      @Override
      public void recordSuccess(
          com.sunny.datapillar.connector.spi.ConnectorInvocation invocation, long elapsedMillis) {}

      @Override
      public void recordFailure(
          com.sunny.datapillar.connector.spi.ConnectorInvocation invocation,
          long elapsedMillis,
          Throwable throwable) {}
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectorAuditLogger connectorAuditLogger() {
    return new ConnectorAuditLogger() {
      @Override
      public void logSuccess(
          com.sunny.datapillar.connector.spi.ConnectorInvocation invocation, long elapsedMillis) {}

      @Override
      public void logFailure(
          com.sunny.datapillar.connector.spi.ConnectorInvocation invocation,
          long elapsedMillis,
          Throwable throwable) {}
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectorKernel connectorKernel(
      ConnectorRegistry registry,
      ConnectorContextResolver contextResolver,
      IdempotencyGuard idempotencyGuard,
      RetryExecutor retryExecutor,
      TimeoutExecutor timeoutExecutor,
      RuntimeErrorMapper runtimeErrorMapper,
      ConnectorMetricsRecorder metricsRecorder,
      ConnectorAuditLogger auditLogger) {
    return new DefaultConnectorKernel(
        registry,
        contextResolver,
        idempotencyGuard,
        retryExecutor,
        timeoutExecutor,
        runtimeErrorMapper,
        metricsRecorder,
        auditLogger);
  }
}
