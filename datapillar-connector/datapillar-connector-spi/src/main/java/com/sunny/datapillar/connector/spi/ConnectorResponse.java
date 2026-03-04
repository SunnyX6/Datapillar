package com.sunny.datapillar.connector.spi;

import com.fasterxml.jackson.databind.JsonNode;

/** Connector invocation output payload. */
public record ConnectorResponse(JsonNode payload) {

  public static ConnectorResponse of(JsonNode payload) {
    return new ConnectorResponse(payload);
  }
}
