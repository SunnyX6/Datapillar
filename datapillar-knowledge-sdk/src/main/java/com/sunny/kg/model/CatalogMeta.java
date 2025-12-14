package com.sunny.kg.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据目录元数据
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
@Builder
public class CatalogMeta {

    /**
     * 目录名称
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 目录描述
     */
    private String description;

    /**
     * 数据范围
     */
    private String dataScope;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 扩展属性
     */
    @Builder.Default
    private Map<String, Object> extra = Map.of();

}
