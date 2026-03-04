package com.sunny.datapillar.connector.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/** Internal connector invocation model. */
public record ConnectorInvocation(
    String connectorId,
    String operationId,
    JsonNode payload,
    ConnectorContext context,
    IdempotencyDescriptor idempotency,
    TimeoutDescriptor timeout) {

  public ConnectorInvocation {
    if (connectorId == null || connectorId.isBlank()) {
      throw new IllegalArgumentException("Connector id must not be blank");
    }
    if (operationId == null || operationId.isBlank()) {
      throw new IllegalArgumentException("Operation id must not be blank");
    }
    payload = payload == null ? JsonNodeFactory.instance.objectNode() : payload;
  }

  public static Builder builder(String connectorId, String operationId) {
    return new Builder(connectorId, operationId);
  }

  public static final class Builder {
    private final String connectorId;
    private final String operationId;
    private JsonNode payload;
    private ConnectorContext context;
    private IdempotencyDescriptor idempotency;
    private TimeoutDescriptor timeout;

    private Builder(String connectorId, String operationId) {
      this.connectorId = connectorId;
      this.operationId = operationId;
    }

    public Builder payload(JsonNode payload) {
      this.payload = payload;
      return this;
    }

    public Builder context(ConnectorContext context) {
      this.context = context;
      return this;
    }

    public Builder idempotency(IdempotencyDescriptor idempotency) {
      this.idempotency = idempotency;
      return this;
    }

    public Builder timeout(TimeoutDescriptor timeout) {
      this.timeout = timeout;
      return this;
    }

    public ConnectorInvocation build() {
      return new ConnectorInvocation(
          connectorId, operationId, payload, context, idempotency, timeout);
    }
  }
}
