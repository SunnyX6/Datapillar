package com.sunny.datapillar.studio.module.sql.service;

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

/**
 * SQLservice provideSQLBusiness capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface SqlService {

  /**
   * execute SQL
   *
   * @param request Execute request
   * @return Execution result
   */
  SqlExecuteResponse executeSql(SqlExecuteRequest request);

  /**
   * Get Catalog list
   *
   * @return Catalog list
   */
  SqlCatalogListResponse listCatalogs();

  /**
   * Get Database list
   *
   * @param catalog Catalog Name（Optional）
   * @return Database list
   */
  SqlDatabaseListResponse listDatabases(String catalog);

  /**
   * Get Table list
   *
   * @param catalog Catalog Name（Optional）
   * @param database Database Name（Optional）
   * @return Table list
   */
  SqlTableListResponse listTables(String catalog, String database);

  /**
   * Check if the service is available
   *
   * @return Is it available
   */
  boolean isAvailable();
}
