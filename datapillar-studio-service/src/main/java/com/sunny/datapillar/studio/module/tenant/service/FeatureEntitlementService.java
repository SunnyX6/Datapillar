package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureEntitlementDto;
import java.util.List;

/**
 * 功能Entitlement服务
 * 提供功能Entitlement业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface FeatureEntitlementService {

    List<FeatureEntitlementDto.Item> listEntitlements();

    FeatureEntitlementDto.Item getEntitlement(Long objectId);

    void updateEntitlement(Long objectId, FeatureEntitlementDto.UpdateItem item);

    void updateEntitlements(List<FeatureEntitlementDto.UpdateItem> items);
}
