package com.sunny.datapillar.connector.airflow.error;

import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ErrorType;
import java.io.IOException;

/** Maps airflow HTTP failures to connector exceptions. */
public class AirflowErrorMapper {

  public ConnectorException fromHttpStatus(int statusCode, String responseBody) {
    String message =
        "Airflow request failed: status=" + statusCode + ", body=" + safeBody(responseBody);
    if (statusCode == 400) {
      return new ConnectorException(ErrorType.BAD_REQUEST, message);
    }
    if (statusCode == 401) {
      return new ConnectorException(ErrorType.UNAUTHORIZED, message);
    }
    if (statusCode == 403) {
      return new ConnectorException(ErrorType.FORBIDDEN, message);
    }
    if (statusCode == 404) {
      return new ConnectorException(ErrorType.NOT_FOUND, message);
    }
    if (statusCode == 409) {
      return new ConnectorException(ErrorType.CONFLICT, message);
    }
    if (statusCode >= 500) {
      return new ConnectorException(ErrorType.SERVICE_UNAVAILABLE, message);
    }
    return new ConnectorException(ErrorType.BAD_GATEWAY, message);
  }

  public ConnectorException fromIo(IOException ioException) {
    return new ConnectorException(
        ErrorType.BAD_GATEWAY,
        "Airflow connectivity failure: " + ioException.getMessage(),
        ioException);
  }

  public ConnectorException fromUnexpected(Exception exception) {
    return new ConnectorException(
        ErrorType.INTERNAL_ERROR,
        "Airflow connector failure: " + exception.getMessage(),
        exception);
  }

  private String safeBody(String responseBody) {
    if (responseBody == null) {
      return "";
    }
    String normalized = responseBody.trim();
    if (normalized.length() <= 512) {
      return normalized;
    }
    return normalized.substring(0, 512);
  }
}
