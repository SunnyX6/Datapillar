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
 * invitation service Provide invitation business capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface InvitationService {

  /** Create invitation(Tenant administrator operations). */
  InvitationCreateResponse createInvitation(InvitationCreateRequest dto);

  /** Query invitation details based on invitation code(Accessible anonymously). */
  InvitationDetailResponse getInvitationByCode(String inviteCode);

  /** Invite registration(Anonymous user operations). */
  void registerInvitation(InvitationRegisterRequest request);
}
