package com.sunny.datapillar.studio.module.tenant.service.impl;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.entity.FeatureObject;
import com.sunny.datapillar.studio.module.tenant.mapper.FeatureObjectMapper;
import com.sunny.datapillar.studio.module.tenant.service.FeatureObjectService;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;

import lombok.RequiredArgsConstructor;
import com.sunny.datapillar.common.exception.UnauthorizedException;

/**
 * 功能Object服务实现
 * 实现功能Object业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class FeatureObjectServiceImpl implements FeatureObjectService {

    private final FeatureObjectMapper featureObjectMapper;

    @Override
    public List<FeatureTreeNodeItem> getFeatureObjectsByUserId(Long userId, String location) {
        Long tenantId = getRequiredTenantId();
        List<FeatureObject> featureObjects = featureObjectMapper.findByUserId(tenantId, userId, location);
        List<FeatureTreeNodeItem> nodes = convertToFeatureObjectNodes(featureObjects);
        return buildFeatureObjectTree(nodes);
    }

    @Override
    public List<FeatureTreeNodeItem> getAllVisibleFeatureObjects() {
        Long tenantId = getRequiredTenantId();
        List<FeatureObject> featureObjects = featureObjectMapper.findAllVisible(tenantId);
        List<FeatureTreeNodeItem> nodes = convertToFeatureObjectNodes(featureObjects);
        return buildFeatureObjectTree(nodes);
    }

    @Override
    public List<FeatureTreeNodeItem> buildFeatureObjectTree(List<FeatureTreeNodeItem> featureObjects) {
        if (featureObjects == null || featureObjects.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<FeatureTreeNodeItem>> featureObjectMap = featureObjects.stream()
                .collect(Collectors.groupingBy(node -> node.getParentId() == null ? 0L : node.getParentId()));

        List<FeatureTreeNodeItem> rootNodes = featureObjectMap.getOrDefault(0L, new ArrayList<>());
        buildChildren(rootNodes, featureObjectMap);

        rootNodes.sort((n1, n2) -> {
            int sort1 = n1.getSort() != null ? n1.getSort() : 0;
            int sort2 = n2.getSort() != null ? n2.getSort() : 0;
            return Integer.compare(sort1, sort2);
        });

        return rootNodes;
    }

    private void buildChildren(List<FeatureTreeNodeItem> parentNodes, Map<Long, List<FeatureTreeNodeItem>> featureObjectMap) {
        for (FeatureTreeNodeItem parentNode : parentNodes) {
            List<FeatureTreeNodeItem> children = featureObjectMap.getOrDefault(parentNode.getId(), new ArrayList<>());
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

    private List<FeatureTreeNodeItem> convertToFeatureObjectNodes(List<FeatureObject> featureObjects) {
        return featureObjects.stream()
                .map(this::convertToFeatureObjectNode)
                .collect(Collectors.toList());
    }

    private FeatureTreeNodeItem convertToFeatureObjectNode(FeatureObject featureObject) {
        FeatureTreeNodeItem response = new FeatureTreeNodeItem();
        BeanUtils.copyProperties(featureObject, response);
        return response;
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("未授权访问");
        }
        return tenantId;
    }

}
