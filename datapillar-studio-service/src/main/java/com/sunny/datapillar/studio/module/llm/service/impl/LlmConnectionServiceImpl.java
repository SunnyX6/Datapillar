package com.sunny.datapillar.studio.module.llm.service.impl;

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
import com.sunny.datapillar.studio.module.llm.service.LlmConnectionService;
import com.sunny.datapillar.studio.module.llm.service.LlmManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * large modelConnectionService implementation Implement large modelsConnectionBusiness process and
 * rule verification
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class LlmConnectionServiceImpl implements LlmConnectionService {

  private final LlmManagerService llmManagerService;

  @Override
  public LlmModelConnectResponse connectModel(
      Long userId, Long aiModelId, LlmModelConnectRequest request) {
    return llmManagerService.connectModel(userId, aiModelId, request);
  }
}
