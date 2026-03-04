package com.sunny.datapillar.auth.error;

/** Auth error code enum. */
public enum AuthErrorCode {
  BAD_REQUEST(40001),
  UNAUTHORIZED(40101),
  FORBIDDEN(40301),
  NOT_FOUND(40401),
  INTERNAL_ERROR(50001),
  SERVICE_UNAVAILABLE(50301);

  private final int code;

  AuthErrorCode(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
