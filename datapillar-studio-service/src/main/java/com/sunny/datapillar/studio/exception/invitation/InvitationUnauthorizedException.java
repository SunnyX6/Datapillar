package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import java.util.Map;

/**
 * Unauthorized invitation exception Describe the missing semantics of tenant context in the
 * invitation process
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationUnauthorizedException extends UnauthorizedException {

  public InvitationUnauthorizedException() {
    super(ErrorType.INVITATION_UNAUTHORIZED, Map.of(), "Unauthorized access");
  }

  public InvitationUnauthorizedException(Throwable cause) {
    super(cause, ErrorType.INVITATION_UNAUTHORIZED, Map.of(), "Unauthorized access");
  }
}
