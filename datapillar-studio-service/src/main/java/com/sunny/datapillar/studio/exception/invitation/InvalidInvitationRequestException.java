package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * Invalid invitation request parameters exception Describes the illegal semantics of the invitation
 * link parameters.
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvalidInvitationRequestException extends BadRequestException {

  public InvalidInvitationRequestException() {
    super(ErrorType.INVITATION_REQUEST_INVALID, Map.of(), "Invitation parameter error");
  }

  public InvalidInvitationRequestException(Throwable cause) {
    super(cause, ErrorType.INVITATION_REQUEST_INVALID, Map.of(), "Invitation parameter error");
  }
}
