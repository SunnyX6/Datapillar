package com.sunny.datapillar.openlineage.web.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** DTO wrapper for /events payload. */
public class EventIngestRequest {

  private final Map<String, Object> payload = new LinkedHashMap<>();

  @JsonAnySetter
  public void putPayloadField(String key, Object value) {
    payload.put(key, value);
  }

  @JsonIgnore
  public JsonNode toPayloadNode(ObjectMapper objectMapper) {
    return objectMapper.valueToTree(payload);
  }
}
