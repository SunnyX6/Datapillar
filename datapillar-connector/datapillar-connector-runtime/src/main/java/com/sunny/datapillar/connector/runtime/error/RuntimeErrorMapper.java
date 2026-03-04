package com.sunny.datapillar.connector.runtime.error;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.ConflictException;
import com.sunny.datapillar.common.exception.ConnectionFailedException;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.connector.spi.ConnectorException;

/** Runtime exception mapper from connector exceptions to platform exceptions. */
public class RuntimeErrorMapper {

  public RuntimeException map(Throwable throwable) {
    if (throwable instanceof DatapillarRuntimeException runtimeException) {
      return runtimeException;
    }
    if (throwable instanceof ConnectorException connectorException) {
      return mapConnectorException(connectorException);
    }
    if (throwable instanceof RuntimeException runtimeException) {
      return new InternalException(
          runtimeException,
          runtimeException.getMessage() == null
              ? "Connector invocation failed"
              : runtimeException.getMessage());
    }
    return new InternalException(throwable, "Connector invocation failed");
  }

  private RuntimeException mapConnectorException(ConnectorException exception) {
    String errorType = exception.errorType();
    String message =
        exception.getMessage() == null ? "Connector invocation failed" : exception.getMessage();

    if (ErrorType.BAD_REQUEST.equals(errorType)) {
      return new BadRequestException(exception, message);
    }
    if (ErrorType.UNAUTHORIZED.equals(errorType)) {
      return new UnauthorizedException(exception, message);
    }
    if (ErrorType.FORBIDDEN.equals(errorType)) {
      return new ForbiddenException(exception, message);
    }
    if (ErrorType.NOT_FOUND.equals(errorType)) {
      return new NotFoundException(exception, message);
    }
    if (ErrorType.ALREADY_EXISTS.equals(errorType)) {
      return new AlreadyExistsException(exception, message);
    }
    if (ErrorType.CONFLICT.equals(errorType)) {
      return new ConflictException(exception, message);
    }
    if (ErrorType.BAD_GATEWAY.equals(errorType)) {
      return new ConnectionFailedException(exception, message);
    }
    if (ErrorType.SERVICE_UNAVAILABLE.equals(errorType)) {
      return new ServiceUnavailableException(exception, message);
    }
    return new InternalException(exception, message);
  }
}
