package com.sunny.datapillar.connector.gravitino.transport.sdk;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ErrorType;
import org.apache.gravitino.rest.RESTRequest;

/** Generic JSON request payload for Gravitino RESTClient. */
public class JsonPayloadRequest implements RESTRequest {

  private final JsonNode payload;

  public JsonPayloadRequest(JsonNode payload) {
    this.payload = payload;
  }

  @JsonValue
  public JsonNode payload() {
    return payload;
  }

  @Override
  public void validate() throws IllegalArgumentException {
    if (payload == null || !payload.isObject()) {
      throw new ConnectorException(
          ErrorType.BAD_REQUEST, "Gravitino request payload must be a JSON object");
    }
  }
}
