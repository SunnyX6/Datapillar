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
import java.util.List;


/**
 * 功能Object服务
 * 提供功能Object业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface FeatureObjectService {

    /**
     * 根据用户ID查询可访问的功能对象列表
     */
    List<FeatureTreeNodeItem> getFeatureObjectsByUserId(Long userId, String location);

    /**
     * 查询所有可见功能对象
     */
    List<FeatureTreeNodeItem> getAllVisibleFeatureObjects();

    /**
     * 构建功能对象树
     */
    List<FeatureTreeNodeItem> buildFeatureObjectTree(List<FeatureTreeNodeItem> featureObjects);
}
