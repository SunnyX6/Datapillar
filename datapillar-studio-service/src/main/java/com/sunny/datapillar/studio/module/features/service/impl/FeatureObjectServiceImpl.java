package com.sunny.datapillar.studio.module.features.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.features.dto.FeatureObjectDto;
import com.sunny.datapillar.studio.module.features.entity.FeatureObject;
import com.sunny.datapillar.studio.module.features.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.features.service.FeatureObjectService;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 功能对象服务实现类
 *
 * @author sunny
 */
@Service
@RequiredArgsConstructor
public class FeatureObjectServiceImpl implements FeatureObjectService {

    private final FeatureObjectMapper featureObjectMapper;

    @Override
    public List<FeatureObjectDto.TreeNode> getFeatureObjectsByUserId(Long userId, String location) {
        Long tenantId = getRequiredTenantId();
        List<FeatureObject> featureObjects = featureObjectMapper.findByUserId(tenantId, userId, location);
        List<FeatureObjectDto.TreeNode> nodes = convertToFeatureObjectNodes(featureObjects);
        return buildFeatureObjectTree(nodes);
    }

    @Override
    public List<FeatureObjectDto.TreeNode> getAllVisibleFeatureObjects() {
        Long tenantId = getRequiredTenantId();
        List<FeatureObject> featureObjects = featureObjectMapper.findAllVisible(tenantId);
        List<FeatureObjectDto.TreeNode> nodes = convertToFeatureObjectNodes(featureObjects);
        return buildFeatureObjectTree(nodes);
    }

    @Override
    public List<FeatureObjectDto.TreeNode> buildFeatureObjectTree(List<FeatureObjectDto.TreeNode> featureObjects) {
        if (featureObjects == null || featureObjects.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<FeatureObjectDto.TreeNode>> featureObjectMap = featureObjects.stream()
                .collect(Collectors.groupingBy(node -> node.getParentId() == null ? 0L : node.getParentId()));

        List<FeatureObjectDto.TreeNode> rootNodes = featureObjectMap.getOrDefault(0L, new ArrayList<>());
        buildChildren(rootNodes, featureObjectMap);

        rootNodes.sort((n1, n2) -> {
            int sort1 = n1.getSort() != null ? n1.getSort() : 0;
            int sort2 = n2.getSort() != null ? n2.getSort() : 0;
            return Integer.compare(sort1, sort2);
        });

        return rootNodes;
    }

    private void buildChildren(List<FeatureObjectDto.TreeNode> parentNodes, Map<Long, List<FeatureObjectDto.TreeNode>> featureObjectMap) {
        for (FeatureObjectDto.TreeNode parentNode : parentNodes) {
            List<FeatureObjectDto.TreeNode> children = featureObjectMap.getOrDefault(parentNode.getId(), new ArrayList<>());
            if (!children.isEmpty()) {
                children.sort((n1, n2) -> {
                    int sort1 = n1.getSort() != null ? n1.getSort() : 0;
                    int sort2 = n2.getSort() != null ? n2.getSort() : 0;
                    return Integer.compare(sort1, sort2);
                });
                parentNode.setChildren(children);
                buildChildren(children, featureObjectMap);
            }
        }
    }

    private List<FeatureObjectDto.TreeNode> convertToFeatureObjectNodes(List<FeatureObject> featureObjects) {
        return featureObjects.stream()
                .map(this::convertToFeatureObjectNode)
                .collect(Collectors.toList());
    }

    private FeatureObjectDto.TreeNode convertToFeatureObjectNode(FeatureObject featureObject) {
        FeatureObjectDto.TreeNode response = new FeatureObjectDto.TreeNode();
        BeanUtils.copyProperties(featureObject, response);
        return response;
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return tenantId;
    }

}
