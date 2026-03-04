package com.sunny.datapillar.connector.gravitino.mapper.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Response mapper utilities for Gravitino connector. */
public final class GravitinoResponseMapper {

  private GravitinoResponseMapper() {}

  public static JsonNode toJsonNode(Object value, ObjectMapper objectMapper) {
    if (value == null) {
      return objectMapper.createObjectNode();
    }
    return objectMapper.valueToTree(value);
  }
}
