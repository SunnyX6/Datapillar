package com.sunny.datapillar.studio.module.workflow.service;

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
import java.util.List;


/**
 * 任务Component服务
 * 提供任务Component业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface JobComponentService {

    /**
     * 查询所有可用组件
     */
    List<JobComponentResponse> getAllComponents();

    /**
     * 根据 code 查询组件
     */
    JobComponentResponse getComponentByCode(String code);

    /**
     * 根据类型查询组件
     */
    List<JobComponentResponse> getComponentsByType(String type);
}
