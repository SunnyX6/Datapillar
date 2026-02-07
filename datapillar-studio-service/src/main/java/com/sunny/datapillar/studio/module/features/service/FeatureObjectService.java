package com.sunny.datapillar.studio.module.features.service;

import java.util.List;

import com.sunny.datapillar.studio.module.features.dto.FeatureObjectDto;

/**
 * 功能对象服务接口
 *
 * @author sunny
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
