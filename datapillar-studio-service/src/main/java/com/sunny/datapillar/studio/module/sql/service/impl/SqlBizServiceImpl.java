package com.sunny.datapillar.studio.module.sql.service.impl;

import com.sunny.datapillar.studio.module.sql.dto.SqlDto;
import com.sunny.datapillar.studio.module.sql.service.SqlBizService;
import com.sunny.datapillar.studio.module.sql.service.SqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SQL业务服务实现
 * 实现SQL业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class SqlBizServiceImpl implements SqlBizService {

    private final SqlService sqlService;

    @Override
    public SqlDto.ExecuteResult executeSql(SqlDto.ExecuteRequest request) {
        return sqlService.executeSql(request);
    }

    @Override
    public SqlDto.CatalogListResponse listCatalogs() {
        return sqlService.listCatalogs();
    }

    @Override
    public SqlDto.DatabaseListResponse listDatabases(String catalog) {
        return sqlService.listDatabases(catalog);
    }

    @Override
    public SqlDto.TableListResponse listTables(String catalog, String database) {
        return sqlService.listTables(catalog, database);
    }

    @Override
    public boolean isAvailable() {
        return sqlService.isAvailable();
    }
}
