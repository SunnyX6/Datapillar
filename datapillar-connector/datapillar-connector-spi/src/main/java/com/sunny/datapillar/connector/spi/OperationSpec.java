package com.sunny.datapillar.connector.spi;

/** Single operation capability metadata. */
public record OperationSpec(String operationId, boolean writeOperation) {

  public OperationSpec {
    if (operationId == null || operationId.isBlank()) {
      throw new IllegalArgumentException("Operation id must not be blank");
    }
  }

  public static OperationSpec read(String operationId) {
    return new OperationSpec(operationId, false);
  }

  public static OperationSpec write(String operationId) {
    return new OperationSpec(operationId, true);
  }
}
