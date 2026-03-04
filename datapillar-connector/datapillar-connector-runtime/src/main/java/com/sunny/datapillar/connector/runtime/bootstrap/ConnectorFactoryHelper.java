package com.sunny.datapillar.connector.runtime.bootstrap;

import com.sunny.datapillar.connector.spi.ConfigOption;
import com.sunny.datapillar.connector.spi.ConnectorFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Startup-time validation utilities for connector factories and options. */
public class ConnectorFactoryHelper {

  public Map<String, ConnectorFactory> validateUniqueIdentifier(List<ConnectorFactory> factories) {
    Map<String, List<ConnectorFactory>> grouped = new HashMap<>();
    for (ConnectorFactory factory : factories) {
      grouped
          .computeIfAbsent(factory.connectorIdentifier(), ignored -> new ArrayList<>())
          .add(factory);
    }

    List<String> conflicts =
        grouped.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(
                entry ->
                    entry.getKey()
                        + " -> "
                        + entry.getValue().stream().map(f -> f.getClass().getName()).toList())
            .toList();
    if (!conflicts.isEmpty()) {
      throw new IllegalStateException("Duplicate connector identifiers: " + conflicts);
    }

    Map<String, ConnectorFactory> factoryMap = new HashMap<>();
    grouped.forEach((connectorId, list) -> factoryMap.put(connectorId, list.getFirst()));
    return factoryMap;
  }

  public void validateRequiredOptions(ConnectorFactory factory, Map<String, String> options) {
    Map<String, String> safeOptions = options == null ? Map.of() : options;
    for (ConfigOption<?> option : factory.requiredOptions()) {
      String value = safeOptions.get(option.key());
      if (value == null || value.isBlank()) {
        throw new IllegalStateException(
            "Missing required option for connector '%s': %s"
                .formatted(factory.connectorIdentifier(), option.key()));
      }
    }
  }

  public void validateUnsupportedOptions(ConnectorFactory factory, Map<String, String> options) {
    Map<String, String> safeOptions = options == null ? Map.of() : options;
    Set<String> supported = new HashSet<>();
    factory.requiredOptions().forEach(option -> supported.add(option.key()));
    factory.optionalOptions().forEach(option -> supported.add(option.key()));

    List<String> unsupported =
        safeOptions.keySet().stream().filter(key -> !supported.contains(key)).sorted().toList();
    if (!unsupported.isEmpty()) {
      throw new IllegalStateException(
          "Unsupported options for connector '%s': %s"
              .formatted(factory.connectorIdentifier(), unsupported));
    }
  }
}
