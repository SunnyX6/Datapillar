package com.sunny.datapillar.studio.integration.gravitino;

import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import org.apache.gravitino.exceptions.NoSuchMetadataObjectException;
import org.apache.gravitino.exceptions.NoSuchMetalakeException;
import org.apache.gravitino.exceptions.RESTException;
import org.springframework.stereotype.Component;

/** Maps Gravitino Java client exceptions to studio exceptions. */
@Component
public class GravitinoExceptionMapper {

  public RuntimeException map(Throwable throwable) {
    if (throwable instanceof DatapillarRuntimeException runtimeException) {
      return runtimeException;
    }
    if (throwable instanceof org.apache.gravitino.exceptions.BadRequestException) {
      return new BadRequestException(throwable, message(throwable));
    }
    if (throwable instanceof org.apache.gravitino.exceptions.UnauthorizedException) {
      return new UnauthorizedException(throwable, message(throwable));
    }
    if (throwable instanceof org.apache.gravitino.exceptions.ForbiddenException) {
      return new ForbiddenException(throwable, message(throwable));
    }
    if (throwable instanceof NoSuchMetalakeException
        || throwable instanceof NoSuchMetadataObjectException
        || throwable instanceof org.apache.gravitino.exceptions.NotFoundException) {
      return new NotFoundException(throwable, message(throwable));
    }
    if (throwable instanceof org.apache.gravitino.exceptions.AlreadyExistsException) {
      return new AlreadyExistsException(throwable, message(throwable));
    }
    if (throwable instanceof RESTException) {
      return new ServiceUnavailableException(throwable, message(throwable));
    }
    return new InternalException(throwable, message(throwable));
  }

  private String message(Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null || message.isBlank()) {
      return "Gravitino invocation failed";
    }
    if (message.length() <= 512) {
      return message;
    }
    return message.substring(0, 512);
  }
}
