package com.sunny.datapillar.connector.gravitino.mapper.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Request mapper utilities for Gravitino connector. */
public final class GravitinoRequestMapper {

  private GravitinoRequestMapper() {}

  public static Map<String, String> toQueryMap(JsonNode queryNode, ObjectMapper objectMapper) {
    if (queryNode == null || queryNode.isNull()) {
      return Map.of();
    }
    return objectMapper.convertValue(
        queryNode,
        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
  }
}
