package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * 邀请发起人不存在异常
 * 描述邀请流程中发起人不存在语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationInviterNotFoundException extends NotFoundException {

    public InvitationInviterNotFoundException(String message, Object... args) {
        super(ErrorType.INVITATION_INVITER_NOT_FOUND, Map.of(), message, args);
    }

    public InvitationInviterNotFoundException(Throwable cause, String message, Object... args) {
        super(cause, ErrorType.INVITATION_INVITER_NOT_FOUND, Map.of(), message, args);
    }
}
