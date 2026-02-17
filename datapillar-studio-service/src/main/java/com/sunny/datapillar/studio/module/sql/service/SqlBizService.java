package com.sunny.datapillar.studio.module.sql.service;

import com.sunny.datapillar.studio.module.sql.dto.SqlDto;

/**
 * SQL业务服务
 * 提供SQL业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface SqlBizService {

    SqlDto.ExecuteResult executeSql(SqlDto.ExecuteRequest request);

    SqlDto.CatalogListResponse listCatalogs();

    SqlDto.DatabaseListResponse listDatabases(String catalog);

    SqlDto.TableListResponse listTables(String catalog, String database);

    boolean isAvailable();
}
