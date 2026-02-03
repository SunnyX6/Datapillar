package com.sunny.datapillar.platform.module.features.service;

import com.sunny.datapillar.platform.module.features.dto.FeatureEntitlementDto;
import java.util.List;

/**
 * 租户功能授权服务
 */
public interface FeatureEntitlementService {

    List<FeatureEntitlementDto.Item> listEntitlements();

    FeatureEntitlementDto.Item getEntitlement(Long objectId);

    void updateEntitlement(Long objectId, FeatureEntitlementDto.UpdateItem item);

    void updateEntitlements(List<FeatureEntitlementDto.UpdateItem> items);
}
