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
import com.sunny.datapillar.studio.module.llm.service.LlmBizService;
import com.sunny.datapillar.studio.module.llm.service.LlmManagerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 大模型业务服务实现
 * 实现大模型业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class LlmBizServiceImpl implements LlmBizService {

    private final LlmManagerService llmManagerService;

    @Override
    public List<LlmUserModelPermissionResponse> listCurrentUserModelPermissions(Long currentUserId,
                                                                                          boolean onlyEnabled) {
        return llmManagerService.listUserModelPermissions(currentUserId, currentUserId, onlyEnabled);
    }

    @Override
    public LlmUserModelUsageResponse setCurrentUserDefaultModel(Long currentUserId, Long aiModelId) {
        return llmManagerService.setUserDefaultModel(currentUserId, currentUserId, aiModelId);
    }
}
