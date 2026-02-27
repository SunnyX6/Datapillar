package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * 邀请请求参数无效异常
 * 描述邀请链路参数不合法语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvalidInvitationRequestException extends BadRequestException {

    public InvalidInvitationRequestException() {
        super(ErrorType.INVITATION_REQUEST_INVALID, Map.of(), "邀请参数错误");
    }

    public InvalidInvitationRequestException(Throwable cause) {
        super(cause, ErrorType.INVITATION_REQUEST_INVALID, Map.of(), "邀请参数错误");
    }
}
