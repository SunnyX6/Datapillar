package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * SSO Invalid configuration request parameter exception Description SSO Configure request
 * parameters with illegal semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvalidSsoConfigRequestException extends BadRequestException {

  public InvalidSsoConfigRequestException() {
    super(ErrorType.SSO_CONFIG_REQUEST_INVALID, Map.of(), "SSOConfiguration parameter error");
  }

  public InvalidSsoConfigRequestException(Throwable cause) {
    super(
        cause, ErrorType.SSO_CONFIG_REQUEST_INVALID, Map.of(), "SSOConfiguration parameter error");
  }
}
