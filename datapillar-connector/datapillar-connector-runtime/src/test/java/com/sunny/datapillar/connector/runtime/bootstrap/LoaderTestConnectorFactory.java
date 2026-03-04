package com.sunny.datapillar.connector.runtime.bootstrap;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sunny.datapillar.connector.spi.ConfigOption;
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorFactory;
import com.sunny.datapillar.connector.spi.ConnectorFactoryContext;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.ConnectorManifest;
import com.sunny.datapillar.connector.spi.ConnectorResponse;
import com.sunny.datapillar.connector.spi.OperationSpec;
import java.util.Map;
import java.util.Set;

public class LoaderTestConnectorFactory implements ConnectorFactory {

  @Override
  public String connectorIdentifier() {
    return "loader-test";
  }

  @Override
  public Set<ConfigOption<?>> requiredOptions() {
    return Set.of();
  }

  @Override
  public Set<ConfigOption<?>> optionalOptions() {
    return Set.of();
  }

  @Override
  public Connector create(ConnectorFactoryContext context) {
    return new Connector() {
      @Override
      public ConnectorManifest manifest() {
        return new ConnectorManifest(
            "loader-test", "1.0.0", Map.of("loader.test.op", OperationSpec.read("loader.test.op")));
      }

      @Override
      public ConnectorResponse invoke(ConnectorInvocation invocation) {
        return ConnectorResponse.of(JsonNodeFactory.instance.objectNode().put("ok", true));
      }
    };
  }
}
