package com.sunny.datapillar.connector.gravitino.error;

import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ErrorType;
import org.apache.gravitino.exceptions.AlreadyExistsException;
import org.apache.gravitino.exceptions.BadRequestException;
import org.apache.gravitino.exceptions.ForbiddenException;
import org.apache.gravitino.exceptions.NoSuchMetadataObjectException;
import org.apache.gravitino.exceptions.NoSuchMetalakeException;
import org.apache.gravitino.exceptions.NotFoundException;
import org.apache.gravitino.exceptions.RESTException;
import org.apache.gravitino.exceptions.UnauthorizedException;

/** Maps Gravitino client exceptions to connector errors. */
public class GravitinoErrorMapper {

  public ConnectorException map(Throwable throwable) {
    if (throwable instanceof ConnectorException connectorException) {
      return connectorException;
    }
    if (throwable instanceof BadRequestException) {
      return new ConnectorException(ErrorType.BAD_REQUEST, message(throwable), throwable);
    }
    if (throwable instanceof UnauthorizedException) {
      return new ConnectorException(ErrorType.UNAUTHORIZED, message(throwable), throwable);
    }
    if (throwable instanceof ForbiddenException) {
      return new ConnectorException(ErrorType.FORBIDDEN, message(throwable), throwable);
    }
    if (throwable instanceof NoSuchMetalakeException
        || throwable instanceof NoSuchMetadataObjectException
        || throwable instanceof NotFoundException) {
      return new ConnectorException(ErrorType.NOT_FOUND, message(throwable), throwable);
    }
    if (throwable instanceof AlreadyExistsException) {
      return new ConnectorException(ErrorType.ALREADY_EXISTS, message(throwable), throwable);
    }
    if (throwable instanceof RESTException restException) {
      return new ConnectorException(ErrorType.BAD_GATEWAY, message(restException), restException);
    }
    return new ConnectorException(ErrorType.INTERNAL_ERROR, message(throwable), throwable);
  }

  private String message(Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null || message.isBlank()) {
      return "Gravitino connector invocation failed";
    }
    if (message.length() <= 512) {
      return message;
    }
    return message.substring(0, 512);
  }
}
