package com.sunny.datapillar.studio.exception.invitation;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * 邀请租户不存在异常
 * 描述邀请流程中租户资源不存在语义
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvitationTenantNotFoundException extends NotFoundException {

    public InvitationTenantNotFoundException(String message, Object... args) {
        super(ErrorType.INVITATION_TENANT_NOT_FOUND, Map.of(), message, args);
    }

    public InvitationTenantNotFoundException(Throwable cause, String message, Object... args) {
        super(cause, ErrorType.INVITATION_TENANT_NOT_FOUND, Map.of(), message, args);
    }
}
