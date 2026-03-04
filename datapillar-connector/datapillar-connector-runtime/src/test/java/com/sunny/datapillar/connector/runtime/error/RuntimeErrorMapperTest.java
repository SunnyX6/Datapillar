package com.sunny.datapillar.connector.runtime.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ErrorType;
import org.junit.jupiter.api.Test;

class RuntimeErrorMapperTest {

  private final RuntimeErrorMapper mapper = new RuntimeErrorMapper();

  @Test
  void map_shouldConvertConnectorBadRequest() {
    RuntimeException exception =
        mapper.map(new ConnectorException(ErrorType.BAD_REQUEST, "invalid"));

    assertInstanceOf(BadRequestException.class, exception);
    assertEquals("invalid", exception.getMessage());
  }

  @Test
  void map_shouldConvertUnexpectedRuntimeExceptionToInternal() {
    RuntimeException exception = mapper.map(new RuntimeException("boom"));

    assertInstanceOf(InternalException.class, exception);
    assertEquals("boom", exception.getMessage());
  }
}
