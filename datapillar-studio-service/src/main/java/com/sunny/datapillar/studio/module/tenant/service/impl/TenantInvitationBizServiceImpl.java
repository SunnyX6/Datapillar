package com.sunny.datapillar.studio.module.tenant.service.impl;

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
import com.sunny.datapillar.studio.module.tenant.service.InvitationService;
import com.sunny.datapillar.studio.module.tenant.service.TenantInvitationBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Tenant invitation business service implementation Implement tenant invitation business process
 * and rule verification
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantInvitationBizServiceImpl implements TenantInvitationBizService {

  private final InvitationService invitationService;

  @Override
  public InvitationDetailResponse getInvitationByCode(String inviteCode) {
    return invitationService.getInvitationByCode(inviteCode);
  }

  @Override
  public void registerInvitation(InvitationRegisterRequest request) {
    invitationService.registerInvitation(request);
  }
}
