package com.sunny.kg.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 表元数据
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
@Builder
public class TableMeta {

    /**
     * 所属目录
     */
    private String catalog;

    /**
     * 所属分层 (SRC/ODS/DWD/DWS)
     */
    private String schema;

    /**
     * 表名
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 表描述
     */
    private String description;

    /**
     * 字段列表
     */
    private List<ColumnMeta> columns;

    /**
     * 质量分数
     */
    private Integer qualityScore;

    /**
     * 认证级别 (NONE/CERTIFIED/OFFICIAL)
     */
    private String certificationLevel;

    /**
     * 业务价值描述
     */
    private String businessValue;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 示例数据 (JSON 格式)
     */
    private String sampleData;

    /**
     * 扩展属性
     */
    @Builder.Default
    private Map<String, Object> extra = Map.of();

}
