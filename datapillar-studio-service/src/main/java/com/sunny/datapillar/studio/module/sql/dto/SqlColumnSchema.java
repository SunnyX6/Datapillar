package com.sunny.datapillar.studio.module.sql.dto;

/**
 * SQL字段Schema组件
 * 负责SQL字段Schema核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class SqlColumnSchema extends SqlDto.ColumnSchema {

    public SqlColumnSchema() {
    }

    public SqlColumnSchema(String name, String type, boolean nullable) {
        super(name, type, nullable);
    }
}
