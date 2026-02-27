package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;

/**
 * 邀请服务
 * 提供邀请业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface InvitationService {

    /**
     * 创建邀请（租户管理员操作）。
     */
    InvitationCreateResponse createInvitation(InvitationCreateRequest dto);

    /**
     * 根据邀请码查询邀请详情（匿名可访问）。
     */
    InvitationDetailResponse getInvitationByCode(String inviteCode);

    /**
     * 邀请注册（匿名用户操作）。
     */
    void registerInvitation(InvitationRegisterRequest request);
}
