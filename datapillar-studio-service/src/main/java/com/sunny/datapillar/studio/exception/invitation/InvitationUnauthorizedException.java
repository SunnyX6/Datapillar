package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import java.util.Map;

/**
 * 邀请未授权异常
 * 描述邀请流程中租户上下文缺失语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationUnauthorizedException extends UnauthorizedException {

    public InvitationUnauthorizedException() {
        super(ErrorType.INVITATION_UNAUTHORIZED, Map.of(), "未授权访问");
    }

    public InvitationUnauthorizedException(Throwable cause) {
        super(cause, ErrorType.INVITATION_UNAUTHORIZED, Map.of(), "未授权访问");
    }
}
