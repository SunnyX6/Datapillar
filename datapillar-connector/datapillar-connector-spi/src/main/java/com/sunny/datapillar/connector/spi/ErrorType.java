package com.sunny.datapillar.connector.spi;

/** Platform error type aliases for connector modules. */
public final class ErrorType {

  public static final String BAD_REQUEST =
      com.sunny.datapillar.common.constant.ErrorType.BAD_REQUEST;
  public static final String UNAUTHORIZED =
      com.sunny.datapillar.common.constant.ErrorType.UNAUTHORIZED;
  public static final String FORBIDDEN = com.sunny.datapillar.common.constant.ErrorType.FORBIDDEN;
  public static final String NOT_FOUND = com.sunny.datapillar.common.constant.ErrorType.NOT_FOUND;
  public static final String CONFLICT = com.sunny.datapillar.common.constant.ErrorType.CONFLICT;
  public static final String ALREADY_EXISTS =
      com.sunny.datapillar.common.constant.ErrorType.ALREADY_EXISTS;
  public static final String BAD_GATEWAY =
      com.sunny.datapillar.common.constant.ErrorType.BAD_GATEWAY;
  public static final String SERVICE_UNAVAILABLE =
      com.sunny.datapillar.common.constant.ErrorType.SERVICE_UNAVAILABLE;
  public static final String INTERNAL_ERROR =
      com.sunny.datapillar.common.constant.ErrorType.INTERNAL_ERROR;

  private ErrorType() {}
}
