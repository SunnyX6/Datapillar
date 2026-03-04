package com.sunny.datapillar.connector.runtime.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunny.datapillar.connector.spi.ConfigOption;
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorFactory;
import com.sunny.datapillar.connector.spi.ConnectorFactoryContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectorFactoryHelperTest {

  private final ConnectorFactoryHelper helper = new ConnectorFactoryHelper();

  @Test
  void validateUniqueIdentifier_shouldRejectDuplicates() {
    var one = new SimpleFactory("dup", Set.of(), Set.of());
    var two = new SimpleFactory("dup", Set.of(), Set.of());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> helper.validateUniqueIdentifier(List.of(one, two)));

    assertEquals(true, exception.getMessage().contains("Duplicate connector identifiers"));
  }

  @Test
  void validateUniqueIdentifier_shouldReturnFactoryMap() {
    var first = new SimpleFactory("first", Set.of(), Set.of());
    var second = new SimpleFactory("second", Set.of(), Set.of());

    Map<String, ConnectorFactory> result = helper.validateUniqueIdentifier(List.of(first, second));

    assertEquals(2, result.size());
    assertEquals(first, result.get("first"));
    assertEquals(second, result.get("second"));
  }

  @Test
  void validateRequiredOptions_shouldRejectMissingOption() {
    var factory =
        new SimpleFactory("airflow", Set.of(ConfigOption.of("endpoint", String.class)), Set.of());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> helper.validateRequiredOptions(factory, Map.of()));

    assertEquals(true, exception.getMessage().contains("Missing required option"));
  }

  @Test
  void validateUnsupportedOptions_shouldRejectUnknownOption() {
    var factory =
        new SimpleFactory(
            "airflow",
            Set.of(ConfigOption.of("endpoint", String.class)),
            Set.of(ConfigOption.of("username", String.class)));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                helper.validateUnsupportedOptions(
                    factory, Map.of("endpoint", "http://127.0.0.1", "unknown", "x")));

    assertEquals(true, exception.getMessage().contains("Unsupported options"));
  }

  @Test
  void validateUnsupportedOptions_shouldAcceptKnownOptions() {
    var factory =
        new SimpleFactory(
            "airflow",
            Set.of(ConfigOption.of("endpoint", String.class)),
            Set.of(ConfigOption.of("username", String.class)));

    helper.validateRequiredOptions(factory, Map.of("endpoint", "http://127.0.0.1"));
    helper.validateUnsupportedOptions(
        factory, Map.of("endpoint", "http://127.0.0.1", "username", "sunny"));
  }

  private record SimpleFactory(
      String connectorIdentifier,
      Set<ConfigOption<?>> requiredOptions,
      Set<ConfigOption<?>> optionalOptions)
      implements ConnectorFactory {
    @Override
    public Connector create(ConnectorFactoryContext context) {
      throw new UnsupportedOperationException("not required");
    }
  }
}
