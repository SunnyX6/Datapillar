package com.sunny.datapillar.studio.module.sql.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SQL 请求/响应 DTO
 *
 * @author sunny
 */
public class SqlDto {

    /**
     * SQL 执行请求
     */
    @Data
    public static class ExecuteRequest {

        /**
         * SQL 语句
         */
        @NotBlank(message = "SQL 语句不能为空")
        private String sql;

        /**
         * Catalog 名称（可选）
         */
        private String catalog;

        /**
         * Database 名称（可选）
         */
        private String database;

        /**
         * 最大返回行数（可选，默认使用配置值）
         */
        private Integer maxRows;
    }

    /**
     * SQL 执行结果
     */
    @Data
    public static class ExecuteResult {

        /**
         * 执行是否成功
         */
        private boolean success;

        /**
         * 错误信息（如果失败）
         */
        private String error;

        /**
         * 列定义
         */
        private List<ColumnSchema> columns;

        /**
         * 数据行
         */
        private List<List<Object>> rows;

        /**
         * 行数
         */
        private int rowCount;

        /**
         * 是否还有更多数据
         */
        private boolean hasMore;

        /**
         * 执行耗时（毫秒）
         */
        private long executionTime;

        /**
         * 提示信息
         */
        private String message;

        public static ExecuteResult success() {
            ExecuteResult result = new ExecuteResult();
            result.setSuccess(true);
            return result;
        }

        public static ExecuteResult error(String error) {
            ExecuteResult result = new ExecuteResult();
            result.setSuccess(false);
            result.setError(error);
            return result;
        }
    }

    /**
     * 列定义
     */
    @Data
    public static class ColumnSchema {

        /**
         * 列名
         */
        private String name;

        /**
         * 数据类型
         */
        private String type;

        /**
         * 是否可为空
         */
        private boolean nullable;

        public ColumnSchema() {}

        public ColumnSchema(String name, String type, boolean nullable) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
        }
    }

    /**
     * Catalog 列表响应
     */
    @Data
    public static class CatalogListResponse {
        private List<String> catalogs;
        private String currentCatalog;
    }

    /**
     * Database 列表响应
     */
    @Data
    public static class DatabaseListResponse {
        private List<String> databases;
        private String currentDatabase;
    }

    /**
     * Table 列表响应
     */
    @Data
    public static class TableListResponse {
        private List<String> tables;
    }
}
