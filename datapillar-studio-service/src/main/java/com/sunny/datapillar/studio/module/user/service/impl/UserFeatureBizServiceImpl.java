package com.sunny.datapillar.studio.module.user.service.impl;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.tenant.service.FeatureObjectService;
import com.sunny.datapillar.studio.module.user.service.UserFeatureBizService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户功能业务服务实现
 * 实现用户功能业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class UserFeatureBizServiceImpl implements UserFeatureBizService {

    private final FeatureObjectService featureObjectService;

    @Override
    public List<FeatureObjectDto.TreeNode> listFeatures(Long userId, String location) {
        return featureObjectService.getFeatureObjectsByUserId(userId, location);
    }
}
