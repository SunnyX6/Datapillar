package com.sunny.datapillar.workbench.module.sql.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.ResultKind;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.internal.TableEnvironmentImpl;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.DataType;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sunny.datapillar.workbench.module.sql.config.SqlConfig;
import com.sunny.datapillar.workbench.module.sql.config.GravitinoConfig;
import com.sunny.datapillar.workbench.module.sql.dto.SqlDto;
import com.sunny.datapillar.workbench.module.sql.dto.SqlDto.ColumnSchema;
import com.sunny.datapillar.workbench.module.sql.dto.SqlDto.ExecuteResult;
import com.sunny.datapillar.workbench.module.sql.service.SqlService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * SQL 执行服务实现
 *
 * <p>使用 Flink TableEnvironment 执行 SQL，通过 Gravitino Catalog Store 自动加载 Catalog。
 * Flink 会自动管理本地执行环境，无需显式启动 MiniCluster。
 *
 * @author sunny
 */
@Service
public class SqlServiceImpl implements SqlService {

    private static final Logger LOG = LoggerFactory.getLogger(SqlServiceImpl.class);

    private final SqlConfig sqlConfig;
    private final GravitinoConfig gravitinoConfig;

    private TableEnvironment tableEnv;
    private volatile boolean available = false;

    public SqlServiceImpl(SqlConfig sqlConfig, GravitinoConfig gravitinoConfig) {
        this.sqlConfig = sqlConfig;
        this.gravitinoConfig = gravitinoConfig;
    }

    @PostConstruct
    public void init() {
        if (!sqlConfig.isEnabled()) {
            LOG.info("SQL 服务已禁用");
            return;
        }

        try {
            LOG.info("正在初始化 SQL 服务...");
            this.tableEnv = createTableEnvironment();
            this.available = true;
            LOG.info("SQL 服务初始化完成 - Gravitino: {}, Metalake: {}",
                    gravitinoConfig.getUri(), gravitinoConfig.getMetalake());
        } catch (Exception e) {
            LOG.error("SQL 服务初始化失败", e);
            this.available = false;
        }
    }

    @PreDestroy
    public void destroy() {
        LOG.info("正在关闭 SQL 服务...");

        try {
            if (tableEnv != null) {
                TableEnvironmentImpl env = (TableEnvironmentImpl) tableEnv;
                env.getCatalogManager().close();
                LOG.info("SQL 服务已关闭");
            }
        } catch (Exception e) {
            LOG.warn("关闭 TableEnvironment 失败", e);
        }

        this.available = false;
    }

    private TableEnvironment createTableEnvironment() {
        Configuration config = new Configuration();

        // 配置 Gravitino Catalog Store
        config.setString("table.catalog-store.kind", "gravitino");
        config.setString("table.catalog-store.gravitino.gravitino.metalake", gravitinoConfig.getMetalake());
        config.setString("table.catalog-store.gravitino.gravitino.uri", gravitinoConfig.getUri());

        // SQL 执行配置
        config.setString("sql-client.execution.result-mode", "tableau");
        config.setString("sql-client.execution.max-table-result.rows", String.valueOf(sqlConfig.getMaxRows()));
        config.setString("table.exec.resource.default-parallelism", "1");

        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .withConfiguration(config)
                .inBatchMode()
                .build();

        return TableEnvironment.create(settings);
    }

    @Override
    public ExecuteResult executeSql(SqlDto.ExecuteRequest request) {
        if (!available) {
            return ExecuteResult.error("SQL 服务不可用");
        }

        long startTime = System.currentTimeMillis();

        try {
            // 切换 catalog 和 database
            if (request.getCatalog() != null && !request.getCatalog().isEmpty()) {
                tableEnv.useCatalog(request.getCatalog());
            }
            if (request.getDatabase() != null && !request.getDatabase().isEmpty()) {
                tableEnv.useDatabase(request.getDatabase());
            }

            String sql = request.getSql().trim();
            int maxRows = request.getMaxRows() != null ? request.getMaxRows() : sqlConfig.getMaxRows();

            LOG.debug("执行 SQL: {}", sql);

            TableResult tableResult = tableEnv.executeSql(sql);
            ExecuteResult result = buildResult(tableResult, maxRows);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

            LOG.debug("SQL 执行完成, 行数: {}, 耗时: {}ms", result.getRowCount(), result.getExecutionTime());

            return result;

        } catch (Exception e) {
            LOG.error("SQL 执行失败: {}", e.getMessage(), e);
            ExecuteResult errorResult = ExecuteResult.error(e.getMessage());
            errorResult.setExecutionTime(System.currentTimeMillis() - startTime);
            return errorResult;
        }
    }

    private ExecuteResult buildResult(TableResult tableResult, int maxRows) {
        ExecuteResult result = ExecuteResult.success();

        if (tableResult.getResultKind() == ResultKind.SUCCESS) {
            result.setMessage("OK");
            result.setRowCount(0);
            return result;
        }

        if (tableResult.getResultKind() == ResultKind.SUCCESS_WITH_CONTENT) {
            ResolvedSchema schema = tableResult.getResolvedSchema();

            // 列信息
            List<String> columnNames = schema.getColumnNames();
            List<DataType> columnTypes = schema.getColumnDataTypes();
            List<ColumnSchema> columns = new ArrayList<>();
            for (int i = 0; i < columnNames.size(); i++) {
                columns.add(new ColumnSchema(
                        columnNames.get(i),
                        columnTypes.get(i).toString(),
                        columnTypes.get(i).getLogicalType().isNullable()));
            }
            result.setColumns(columns);

            // 数据
            List<List<Object>> rows = new ArrayList<>();
            Iterator<Row> iterator = tableResult.collect();
            int count = 0;
            while (iterator.hasNext() && count < maxRows) {
                Row row = iterator.next();
                List<Object> rowData = new ArrayList<>();
                for (int i = 0; i < row.getArity(); i++) {
                    Object value = row.getField(i);
                    rowData.add(value != null ? value.toString() : null);
                }
                rows.add(rowData);
                count++;
            }
            result.setRows(rows);
            result.setRowCount(count);
            result.setHasMore(iterator.hasNext());
        }

        return result;
    }

    @Override
    public SqlDto.CatalogListResponse listCatalogs() {
        SqlDto.CatalogListResponse response = new SqlDto.CatalogListResponse();
        if (!available) {
            response.setCatalogs(List.of());
            return response;
        }
        response.setCatalogs(Arrays.asList(tableEnv.listCatalogs()));
        response.setCurrentCatalog(tableEnv.getCurrentCatalog());
        return response;
    }

    @Override
    public SqlDto.DatabaseListResponse listDatabases(String catalog) {
        SqlDto.DatabaseListResponse response = new SqlDto.DatabaseListResponse();
        if (!available) {
            response.setDatabases(List.of());
            return response;
        }

        String previousCatalog = tableEnv.getCurrentCatalog();
        try {
            if (catalog != null && !catalog.isEmpty()) {
                tableEnv.useCatalog(catalog);
            }
            response.setDatabases(Arrays.asList(tableEnv.listDatabases()));
            response.setCurrentDatabase(tableEnv.getCurrentDatabase());
        } finally {
            tableEnv.useCatalog(previousCatalog);
        }
        return response;
    }

    @Override
    public SqlDto.TableListResponse listTables(String catalog, String database) {
        SqlDto.TableListResponse response = new SqlDto.TableListResponse();
        if (!available) {
            response.setTables(List.of());
            return response;
        }

        String previousCatalog = tableEnv.getCurrentCatalog();
        String previousDatabase = tableEnv.getCurrentDatabase();
        try {
            if (catalog != null && !catalog.isEmpty()) {
                tableEnv.useCatalog(catalog);
            }
            if (database != null && !database.isEmpty()) {
                tableEnv.useDatabase(database);
            }
            response.setTables(Arrays.asList(tableEnv.listTables()));
        } finally {
            tableEnv.useCatalog(previousCatalog);
            tableEnv.useDatabase(previousDatabase);
        }
        return response;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
