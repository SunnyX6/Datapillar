package com.sunny.datapillar.studio.module.llm.service;

import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import java.util.List;

/**
 * 大模型业务服务
 * 提供大模型业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface LlmBizService {

    List<LlmManagerDto.ModelUsageResponse> listCurrentUserModelUsages(Long currentUserId,
                                                                        boolean onlyEnabled);

    LlmManagerDto.ModelUsageResponse setCurrentUserDefaultModel(Long currentUserId, Long modelId);
}
