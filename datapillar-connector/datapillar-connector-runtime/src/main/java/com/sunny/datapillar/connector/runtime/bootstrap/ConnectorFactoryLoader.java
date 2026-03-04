package com.sunny.datapillar.connector.runtime.bootstrap;

import com.sunny.datapillar.connector.spi.ConnectorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** ServiceLoader based connector factory discovery. */
public class ConnectorFactoryLoader {

  public List<ConnectorFactory> discoverFactories() {
    ServiceLoader<ConnectorFactory> loader = ServiceLoader.load(ConnectorFactory.class);
    List<ConnectorFactory> factories = new ArrayList<>();
    loader.forEach(factories::add);
    return factories;
  }
}
