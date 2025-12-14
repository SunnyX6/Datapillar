package com.sunny.kg.validation;

import com.sunny.kg.model.*;

import java.util.List;

/**
 * 数据校验器
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class Validator {

    private final ValidationConfig config;

    public Validator(ValidationConfig config) {
        this.config = config;
    }

    public void validate(TableMeta table) {
        if (!config.isEnabled()) {
            return;
        }

        validateTableName(table.getName());

        List<ColumnMeta> columns = table.getColumns();
        if (!config.isAllowEmptyColumns() && (columns == null || columns.isEmpty())) {
            throw new ValidationException("columns", "列列表不能为空");
        }

        if (columns != null) {
            if (columns.size() > config.getMaxColumnCount()) {
                throw new ValidationException("columns",
                        String.format("列数量 %d 超过最大限制 %d", columns.size(), config.getMaxColumnCount()));
            }

            for (ColumnMeta column : columns) {
                validateColumnName(column.getName());
            }
        }
    }

    public void validate(Lineage lineage) {
        if (!config.isEnabled()) {
            return;
        }

        if (lineage.getSourceTable() == null || lineage.getSourceTable().isBlank()) {
            throw new ValidationException("sourceTable", "来源表不能为空");
        }
        if (lineage.getTargetTable() == null || lineage.getTargetTable().isBlank()) {
            throw new ValidationException("targetTable", "目标表不能为空");
        }

        validateTableName(lineage.getSourceTable());
        validateTableName(lineage.getTargetTable());
    }

    public void validate(CatalogMeta catalog) {
        if (!config.isEnabled()) {
            return;
        }

        if (catalog.getName() == null || catalog.getName().isBlank()) {
            throw new ValidationException("name", "目录名称不能为空");
        }
    }

    public void validate(SchemaMeta schema) {
        if (!config.isEnabled()) {
            return;
        }

        if (schema.getName() == null || schema.getName().isBlank()) {
            throw new ValidationException("name", "Schema 名称不能为空");
        }
    }

    public void validate(MetricMeta metric) {
        if (!config.isEnabled()) {
            return;
        }

        if (metric.getName() == null || metric.getName().isBlank()) {
            throw new ValidationException("name", "指标名称不能为空");
        }
    }

    public void validate(QualityRuleMeta rule) {
        if (!config.isEnabled()) {
            return;
        }

        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new ValidationException("name", "规则名称不能为空");
        }
    }

    private void validateTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new ValidationException("tableName", "表名不能为空");
        }
        if (tableName.length() > config.getMaxTableNameLength()) {
            throw new ValidationException("tableName",
                    String.format("表名长度 %d 超过最大限制 %d", tableName.length(), config.getMaxTableNameLength()));
        }
        if (!config.getTableNamePattern().matcher(tableName).matches()) {
            throw new ValidationException("tableName",
                    String.format("表名 [%s] 格式不合法", tableName));
        }
    }

    private void validateColumnName(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            throw new ValidationException("columnName", "列名不能为空");
        }
        if (columnName.length() > config.getMaxColumnNameLength()) {
            throw new ValidationException("columnName",
                    String.format("列名长度 %d 超过最大限制 %d", columnName.length(), config.getMaxColumnNameLength()));
        }
        if (!config.getColumnNamePattern().matcher(columnName).matches()) {
            throw new ValidationException("columnName",
                    String.format("列名 [%s] 格式不合法", columnName));
        }
    }

}
