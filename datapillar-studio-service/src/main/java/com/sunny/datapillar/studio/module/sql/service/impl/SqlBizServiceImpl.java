package com.sunny.datapillar.studio.module.sql.service.impl;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
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
    public SqlExecuteResponse executeSql(SqlExecuteRequest request) {
        return sqlService.executeSql(request);
    }

    @Override
    public SqlCatalogListResponse listCatalogs() {
        return sqlService.listCatalogs();
    }

    @Override
    public SqlDatabaseListResponse listDatabases(String catalog) {
        return sqlService.listDatabases(catalog);
    }

    @Override
    public SqlTableListResponse listTables(String catalog, String database) {
        return sqlService.listTables(catalog, database);
    }

    @Override
    public boolean isAvailable() {
        return sqlService.isAvailable();
    }
}
