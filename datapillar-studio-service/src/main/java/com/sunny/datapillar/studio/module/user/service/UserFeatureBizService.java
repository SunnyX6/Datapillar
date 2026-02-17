package com.sunny.datapillar.studio.module.user.service;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;
import java.util.List;

/**
 * 用户功能业务服务
 * 提供用户功能业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface UserFeatureBizService {

    List<FeatureObjectDto.TreeNode> listFeatures(Long userId, String location);
}
