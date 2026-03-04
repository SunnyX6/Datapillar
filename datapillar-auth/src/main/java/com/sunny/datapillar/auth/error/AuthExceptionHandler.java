package com.sunny.datapillar.auth.error;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Unified exception handler for auth APIs. */
@RestControllerAdvice(basePackages = "com.sunny.datapillar.auth.api")
public class AuthExceptionHandler {

  @ExceptionHandler(AuthException.class)
  public ResponseEntity<Map<String, Object>> handleAuthException(AuthException exception) {
    return ResponseEntity.status(exception.getHttpStatus())
        .body(
            Map.of(
                "code", exception.getErrorCode().code(),
                "message", exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
      IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("code", AuthErrorCode.BAD_REQUEST.code(), "message", exception.getMessage()));
  }
}
