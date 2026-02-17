package com.sunny.datapillar.studio.module.tenant.service;

import java.util.List;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureObjectDto;

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
    List<FeatureObjectDto.TreeNode> getFeatureObjectsByUserId(Long userId, String location);

    /**
     * 查询所有可见功能对象
     */
    List<FeatureObjectDto.TreeNode> getAllVisibleFeatureObjects();

    /**
     * 构建功能对象树
     */
    List<FeatureObjectDto.TreeNode> buildFeatureObjectTree(List<FeatureObjectDto.TreeNode> featureObjects);
}
