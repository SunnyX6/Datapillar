package com.sunny.kg.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 字段元数据
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
@Builder
public class ColumnMeta {

    /**
     * 字段名称
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 字段描述
     */
    private String description;

    /**
     * 示例数据
     */
    private List<String> sampleData;

    /**
     * 扩展属性
     */
    @Builder.Default
    private Map<String, Object> extra = Map.of();

}
