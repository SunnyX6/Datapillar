package com.sunny.kg.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 数仓分层元数据
 *
 * @author Sunny
 * @since 2025-12-10
 */
@Data
@Builder
public class SchemaMeta {

    /**
     * 所属目录
     */
    private String catalog;

    /**
     * Schema 名称
     */
    private String name;

    /**
     * 分层标识 (SRC/ODS/DWD/DWS)
     */
    private String layer;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 分层描述
     */
    private String description;

    /**
     * 扩展属性
     */
    @Builder.Default
    private Map<String, Object> extra = Map.of();

}
