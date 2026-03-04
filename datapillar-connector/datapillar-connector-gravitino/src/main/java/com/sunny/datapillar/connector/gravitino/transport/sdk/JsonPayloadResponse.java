package com.sunny.datapillar.connector.gravitino.transport.sdk;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.gravitino.rest.RESTResponse;

/** Generic JSON response payload for Gravitino RESTClient. */
public class JsonPayloadResponse implements RESTResponse {

  private final Map<String, Object> payload = new LinkedHashMap<>();

  @JsonAnySetter
  public void put(String key, Object value) {
    payload.put(key, value);
  }

  public JsonNode toJsonNode(ObjectMapper objectMapper) {
    return objectMapper.valueToTree(payload);
  }

  @Override
  public void validate() throws IllegalArgumentException {}
}
