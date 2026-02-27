package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * 邀请不存在异常
 * 描述邀请码未命中语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationNotFoundException extends NotFoundException {

    public InvitationNotFoundException() {
        super(ErrorType.INVITATION_NOT_FOUND, Map.of(), "邀请码不存在");
    }

    public InvitationNotFoundException(Throwable cause) {
        super(cause, ErrorType.INVITATION_NOT_FOUND, Map.of(), "邀请码不存在");
    }
}
