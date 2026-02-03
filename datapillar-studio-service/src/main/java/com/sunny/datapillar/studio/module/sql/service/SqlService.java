package com.sunny.datapillar.studio.module.sql.service;

import com.sunny.datapillar.studio.module.sql.dto.SqlDto;

/**
 * SQL 执行服务接口
 *
 * @author sunny
 */
public interface SqlService {

    /**
     * 执行 SQL
     *
     * @param request 执行请求
     * @return 执行结果
     */
    SqlDto.ExecuteResult executeSql(SqlDto.ExecuteRequest request);

    /**
     * 获取 Catalog 列表
     *
     * @return Catalog 列表
     */
    SqlDto.CatalogListResponse listCatalogs();

    /**
     * 获取 Database 列表
     *
     * @param catalog Catalog 名称（可选）
     * @return Database 列表
     */
    SqlDto.DatabaseListResponse listDatabases(String catalog);

    /**
     * 获取 Table 列表
     *
     * @param catalog Catalog 名称（可选）
     * @param database Database 名称（可选）
     * @return Table 列表
     */
    SqlDto.TableListResponse listTables(String catalog, String database);

    /**
     * 检查服务是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();
}
