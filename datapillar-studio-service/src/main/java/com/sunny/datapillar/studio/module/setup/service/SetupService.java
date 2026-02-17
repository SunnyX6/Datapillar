package com.sunny.datapillar.studio.module.setup.service;

import com.sunny.datapillar.studio.module.setup.dto.SetupInitializeRequest;
import com.sunny.datapillar.studio.module.setup.dto.SetupInitializeResponse;
import com.sunny.datapillar.studio.module.setup.dto.SetupStatusResponse;

/**
 * 初始化服务
 * 提供初始化业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface SetupService {

    SetupStatusResponse getStatus();

    SetupInitializeResponse initialize(SetupInitializeRequest request);
}
