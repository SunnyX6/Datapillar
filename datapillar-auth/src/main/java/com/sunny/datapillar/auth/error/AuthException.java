package com.sunny.datapillar.auth.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Auth domain exception model. */
@Getter
public class AuthException extends RuntimeException {

  private final AuthErrorCode errorCode;
  private final HttpStatus httpStatus;

  public AuthException(AuthErrorCode errorCode, HttpStatus httpStatus, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  public AuthException(
      AuthErrorCode errorCode, HttpStatus httpStatus, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }
}
