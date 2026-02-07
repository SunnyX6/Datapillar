# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
知识库表相关的工具

工具分层设计：
- list: 列表查询（list_catalogs, list_schemas, list_tables），默认 limit=5
- search: 语义搜索（search_tables, search_columns）
- detail: 获取详情（get_table_detail, get_table_lineage, get_lineage_sql）

设计原则：
- 详情工具入参使用完整路径（path），如 "catalog.schema.table"
- 所有工具出参始终带完整路径信息（catalog, schema, table）
- 搜索工具返回候选列表，每个候选带完整路径
"""

import json
import logging

from pydantic import BaseModel, Field

from src.infrastructure.repository.knowledge import Neo4jColumnSearch, Neo4jTableSearch
from src.modules.etl.tools.registry import etl_tool

logger = logging.getLogger(__name__)


# ==================== 工具参数 Schema ====================


class ListCatalogsInput(BaseModel):
    """列出 Catalog 的参数"""

    limit: int = Field(default=5, ge=1, le=100, description="返回数量上限，默认 5")


class ListSchemasInput(BaseModel):
    """列出 Schema 的参数"""

    catalog: str = Field(..., description="Catalog 名称")
    limit: int = Field(default=5, ge=1, le=100, description="返回数量上限，默认 5")


class ListTablesInput(BaseModel):
    """列出 Table 的参数"""

    catalog: str = Field(..., description="Catalog 名称")
    schema_name: str = Field(..., description="Schema 名称")
    keyword: str | None = Field(default=None, description="按表名关键字过滤（可选）")
    limit: int = Field(default=5, ge=1, le=100, description="返回数量上限，默认 5")


class SearchTablesInput(BaseModel):
    """搜索表的参数"""

    query: str = Field(..., description="搜索关键词或自然语言描述")
    top_k: int = Field(default=10, ge=1, le=50, description="返回数量上限")


class SearchColumnsInput(BaseModel):
    """搜索列的参数"""

    query: str = Field(..., description="搜索关键词或自然语言描述")
    top_k: int = Field(default=10, ge=1, le=50, description="返回数量上限")


class GetTableDetailInput(BaseModel):
    """获取表详情的参数"""

    path: str = Field(..., description="表的完整路径：catalog.schema.table")


class GetTableLineageInput(BaseModel):
    """获取表级血缘的参数"""

    path: str = Field(..., description="表的完整路径：catalog.schema.table")
    direction: str = Field(
        default="both",
        description="血缘方向：upstream（上游）、downstream（下游）、both（双向）",
    )


class GetLineageSqlInput(BaseModel):
    """根据血缘精准查找历史 SQL 的参数"""

    source_tables: list[str] = Field(
        ...,
        description="源表路径列表，格式：catalog.schema.table",
    )
    target_table: str = Field(
        ...,
        description="目标表路径，格式：catalog.schema.table",
    )


# ==================== 内部辅助函数 ====================


def _tool_error(message: str) -> str:
    """构造工具错误响应"""
    return json.dumps({"error": message}, ensure_ascii=False)


def _tool_success(data: dict) -> str:
    """构造工具成功响应"""
    return json.dumps(data, ensure_ascii=False)


def _parse_table_path(path: str) -> tuple[str, str, str] | None:
    """
    解析表路径

    参数：
    - path: 完整路径，格式 catalog.schema.table

    返回：
    - (catalog, schema, table) 或 None（解析失败）
    """
    if not path or not isinstance(path, str):
        return None
    parts = path.strip().split(".")
    if len(parts) != 3:
        return None
    catalog, schema, table = parts
    if not all([catalog.strip(), schema.strip(), table.strip()]):
        return None
    return catalog.strip(), schema.strip(), table.strip()


# ==================== List 工具（第二层） ====================


@etl_tool("list_catalogs", tool_type="Catalog", desc="列出目录", args_schema=ListCatalogsInput)
def list_catalogs(limit: int = 5) -> str:
    """
    列出 Catalog 列表

    ⚠️ 重要：默认只返回前 5 个（折叠显示），不是全部！
    - 如需查看更多，请传入更大的 limit 参数（最大 100）

    输出示例：
    {
        "catalogs": [
            {"name": "hive_prod", "description": "生产环境 Hive"},
            {"name": "mysql_prod", "description": "生产环境 MySQL"}
        ],
        "count": 2
    }
    """
    logger.info(f"list_catalogs(limit={limit})")

    try:
        catalogs = Neo4jTableSearch.list_catalogs(limit=limit)
        return _tool_success({"catalogs": catalogs, "count": len(catalogs)})
    except Exception as e:
        logger.error(f"list_catalogs 执行失败: {e}", exc_info=True)
        return _tool_error("查询失败")


@etl_tool("list_schemas", tool_type="Schema", desc="列出目录下 schema", args_schema=ListSchemasInput)
def list_schemas(catalog: str, limit: int = 5) -> str:
    """
    列出指定 Catalog 下的 Schema 列表

    ⚠️ 重要：默认只返回前 5 个（折叠显示），不是全部！
    - 如需查看更多，请传入更大的 limit 参数（最大 100）

    输入示例：
    {"catalog": "hive_prod"}

    输出示例：
    {
        "catalog": "hive_prod",
        "schemas": [
            {"name": "ods", "path": "hive_prod.ods", "description": "原始数据层"},
            {"name": "dwd", "path": "hive_prod.dwd", "description": "明细数据层"}
        ],
        "count": 2
    }
    """
    logger.info(f"list_schemas(catalog='{catalog}', limit={limit})")

    if not (isinstance(catalog, str) and catalog.strip()):
        return _tool_error("catalog 不能为空")

    catalog = catalog.strip()

    try:
        schemas = Neo4jTableSearch.list_schemas(catalog=catalog, limit=limit)
        if not schemas:
            return _tool_error("未找到任何 Schema")

        # 添加完整路径
        for s in schemas:
            s["path"] = f"{catalog}.{s['name']}"
            s["catalog"] = catalog

        return _tool_success(
            {
                "catalog": catalog,
                "schemas": schemas,
                "count": len(schemas),
            }
        )
    except Exception as e:
        logger.error(f"list_schemas 执行失败: {e}", exc_info=True)
        return _tool_error("查询失败")


@etl_tool("list_tables", tool_type="Table", desc="列出表", args_schema=ListTablesInput)
def list_tables(
    catalog: str,
    schema_name: str,
    keyword: str | None = None,
    limit: int = 5,
) -> str:
    """
    列出指定 Catalog.Schema 下的 Table 列表

    ⚠️ 重要：默认只返回前 5 个（折叠显示），不是全部！
    - 如需查看更多，请传入更大的 limit 参数（最大 100）
    - 可选：使用 keyword 参数按表名关键字过滤

    输入示例：
    {"catalog": "hive_prod", "schema_name": "ods"}

    输出示例：
    {
        "catalog": "hive_prod",
        "schema": "ods",
        "tables": [
            {"name": "t_order", "path": "hive_prod.ods.t_order", "description": "订单表"},
            {"name": "t_user", "path": "hive_prod.ods.t_user", "description": "用户表"}
        ],
        "count": 2
    }
    """
    logger.info(
        f"list_tables(catalog='{catalog}', schema='{schema_name}', keyword='{keyword}', limit={limit})"
    )

    if not (isinstance(catalog, str) and catalog.strip()):
        return _tool_error("catalog 不能为空")
    if not (isinstance(schema_name, str) and schema_name.strip()):
        return _tool_error("schema_name 不能为空")

    catalog = catalog.strip()
    schema_name = schema_name.strip()

    try:
        tables = Neo4jTableSearch.list_tables(
            catalog=catalog,
            schema=schema_name,
            keyword=keyword,
            limit=limit,
        )
        if not tables:
            hint = f"{catalog}.{schema_name}"
            if keyword and str(keyword).strip():
                hint = f"{hint} (keyword={keyword})"
            return _tool_error("未找到任何表")

        # 添加完整路径
        for t in tables:
            t["path"] = f"{catalog}.{schema_name}.{t['name']}"
            t["catalog"] = catalog
            t["schema"] = schema_name

        return _tool_success(
            {
                "catalog": catalog,
                "schema": schema_name,
                "tables": tables,
                "count": len(tables),
            }
        )
    except Exception as e:
        logger.error(f"list_tables 执行失败: {e}", exc_info=True)
        return _tool_error("查询失败")


# ==================== Search 工具（语义搜索） ====================


@etl_tool("search_tables", tool_type="Table", desc="语义搜索表", args_schema=SearchTablesInput)
def search_tables(query: str, top_k: int = 10) -> str:
    """
    搜索表（语义搜索）

    使用场景：
    - 用户问"搜索订单相关的表"、"找一下用户表" → 使用此工具
    - 用户只知道业务概念，不知道具体表名时使用
    - 返回按相关性排序的表列表（带 score）

    ⚠️ 注意：这是语义搜索，不是精确匹配。如果用户知道确切的 catalog/schema，应使用 list_tables

    输入示例：
    {"query": "订单"}

    输出示例：
    {
        "query": "订单",
        "tables": [
            {
                "path": "hive_prod.ods.t_order",
                "catalog": "hive_prod",
                "schema": "ods",
                "table": "t_order",
                "description": "订单主表",
                "score": 0.95
            }
        ],
        "count": 1
    }
    """
    logger.info(f"search_tables(query='{query}', top_k={top_k})")

    if not (isinstance(query, str) and query.strip()):
        return _tool_error("query 不能为空")

    try:
        # 使用向量搜索
        results = Neo4jTableSearch.search_tables(
            query=query.strip(),
            top_k=top_k,
        )

        # 过滤只保留 Table 类型
        tables = []
        for r in results:
            if r.get("type") == "Table":
                path = r.get("path") or ""
                parts = path.split(".")
                if len(parts) >= 3:
                    tables.append(
                        {
                            "path": path,
                            "catalog": parts[0],
                            "schema": parts[1],
                            "table": parts[2],
                            "description": r.get("description") or "",
                            "score": r.get("score", 0),
                        }
                    )

        return _tool_success(
            {
                "query": query.strip(),
                "tables": tables,
                "count": len(tables),
            }
        )
    except Exception as e:
        logger.error(f"search_tables 执行失败: {e}", exc_info=True)
        return _tool_error("查询失败")


@etl_tool("search_columns", tool_type="Column", desc="语义搜索字段", args_schema=SearchColumnsInput)
def search_columns(query: str, top_k: int = 10) -> str:
    """
    搜索列（语义搜索）

    使用场景：
    - 用户问"哪些表有订单状态字段"、"找一下金额相关的列" → 使用此工具
    - 用户想找特定业务含义的字段时使用
    - 返回按相关性排序的列列表（带 score）

    输入示例：
    {"query": "订单状态"}

    输出示例：
    {
        "query": "订单状态",
        "columns": [
            {
                "path": "hive_prod.ods.t_order.order_status",
                "catalog": "hive_prod",
                "schema": "ods",
                "table": "t_order",
                "column": "order_status",
                "dataType": "varchar",
                "description": "订单状态",
                "score": 0.92
            }
        ],
        "count": 1
    }
    """
    logger.info(f"search_columns(query='{query}', top_k={top_k})")

    if not (isinstance(query, str) and query.strip()):
        return _tool_error("query 不能为空")

    try:
        # 使用向量搜索
        results = Neo4jColumnSearch.search_columns(
            query=query.strip(),
            top_k=top_k,
        )

        # 过滤只保留 Column 类型
        columns = []
        for r in results:
            if r.get("type") == "Column":
                path = r.get("path") or ""
                parts = path.split(".")
                if len(parts) >= 4:
                    columns.append(
                        {
                            "path": path,
                            "catalog": parts[0],
                            "schema": parts[1],
                            "table": parts[2],
                            "column": parts[3],
                            "dataType": r.get("dataType") or "",
                            "description": r.get("description") or "",
                            "score": r.get("score", 0),
                        }
                    )

        return _tool_success(
            {
                "query": query.strip(),
                "columns": columns,
                "count": len(columns),
            }
        )
    except Exception as e:
        logger.error(f"search_columns 执行失败: {e}", exc_info=True)
        return _tool_error("查询失败")


# ==================== Detail 工具（第三层） ====================


@etl_tool(
    "get_table_detail",
    tool_type="Table",
    desc="获取表详情（字段/描述）",
    args_schema=GetTableDetailInput,
)
def get_table_detail(path: str) -> str:
    """
    获取表详情（含列和值域）

    使用场景：
    - 用户问"这张表有哪些字段"、"表结构是什么" → 使用此工具
    - 验证表是否存在
    - 获取字段类型、描述、值域等详细信息

    ⚠️ 路径格式：必须是完整路径 catalog.schema.table

    输入示例：
    {"path": "hive_prod.ods.t_order"}

    输出示例：
    {
        "path": "hive_prod.ods.t_order",
        "catalog": "hive_prod",
        "schema": "ods",
        "table": "t_order",
        "description": "订单主表",
        "columns": [
            {"name": "order_id", "dataType": "bigint", "description": "订单ID"},
            {"name": "order_status", "dataType": "varchar", "description": "订单状态"}
        ]
    }
    """
    logger.info(f"get_table_detail(path='{path}')")

    parsed = _parse_table_path(path)
    if not parsed:
        return _tool_error("路径格式错误，应为 catalog.schema.table")

    catalog, schema, table = parsed

    try:
        detail = Neo4jTableSearch.get_table_detail(catalog, schema, table)

        if not detail:
            return _tool_error("未找到表")

        return _tool_success(
            {
                "path": path,
                "catalog": catalog,
                "schema": schema,
                "table": table,
                "description": detail.get("description") or "",
                "columns": detail.get("columns") or [],
            }
        )

    except Exception as e:
        logger.error(f"get_table_detail 执行失败: {e}", exc_info=True)
        return _tool_error("查询失败")


@etl_tool("get_table_lineage", tool_type="Table", desc="获取表血缘", args_schema=GetTableLineageInput)
def get_table_lineage(path: str, direction: str = "both") -> str:
    """
    获取表血缘关系

    使用场景：
    - 用户问"这张表的上游是什么"、"数据从哪里来" → direction="upstream"
    - 用户问"这张表的下游是什么"、"数据流向哪里" → direction="downstream"
    - 用户问"血缘关系" → direction="both"

    ⚠️ 路径格式：必须是完整路径 catalog.schema.table
    ⚠️ direction 参数：upstream（上游）、downstream（下游）、both（双向，默认）

    输入示例：
    {"path": "hive_prod.dwd.order_detail", "direction": "upstream"}

    输出示例：
    {
        "path": "hive_prod.dwd.order_detail",
        "catalog": "hive_prod",
        "schema": "dwd",
        "table": "order_detail",
        "direction": "upstream",
        "upstream": ["hive_prod.ods.t_order", "hive_prod.ods.t_user"],
        "downstream": []
    }
    """
    logger.info(f"get_table_lineage(path='{path}', direction='{direction}')")

    parsed = _parse_table_path(path)
    if not parsed:
        return _tool_error("路径格式错误，应为 catalog.schema.table")

    catalog, schema, table = parsed

    try:
        lineage = Neo4jTableSearch.get_table_lineage(schema, table, direction)

        if not lineage.get("upstream") and not lineage.get("downstream"):
            return _tool_error("未找到表的血缘关系")

        return _tool_success(
            {
                "path": path,
                "catalog": catalog,
                "schema": schema,
                "table": table,
                "direction": direction,
                "upstream": lineage.get("upstream") or [],
                "downstream": lineage.get("downstream") or [],
                "edges": lineage.get("edges") or [],
            }
        )

    except Exception as e:
        logger.error(f"get_table_lineage 执行失败: {e}", exc_info=True)
        return _tool_error("查询失败")


@etl_tool(
    "get_lineage_sql",
    tool_type="Table",
    desc="根据血缘查找历史 SQL",
    args_schema=GetLineageSqlInput,
)
def get_lineage_sql(source_tables: list[str], target_table: str) -> str:
    """
    根据血缘关系精准查找历史 SQL

    使用场景：
    - 需要参考历史 SQL 写法时使用
    - 根据精确的源表 → 目标表关系，查找之前执行过的 SQL
    - 用于 SQL 开发时的参考

    ⚠️ 路径格式：所有表路径必须是完整路径 catalog.schema.table

    输入示例：
    {
        "source_tables": ["hive_prod.ods.t_order", "hive_prod.ods.t_user"],
        "target_table": "hive_prod.dwd.order_detail"
    }

    输出示例：
    {
        "source_tables": ["hive_prod.ods.t_order", "hive_prod.ods.t_user"],
        "target_table": "hive_prod.dwd.order_detail",
        "sql_id": "abc123",
        "sql_content": "INSERT INTO ...",
        "summary": "从订单主表和用户表清洗订单明细",
        "engine": "spark"
    }
    """
    logger.info(f"get_lineage_sql(source={source_tables}, target='{target_table}')")

    # 验证目标表路径
    target_parsed = _parse_table_path(target_table)
    if not target_parsed:
        return _tool_error("目标表路径格式错误，应为 catalog.schema.table")

    # 提取 schema.table 格式（Neo4j 查询使用）
    source_schema_tables = []
    for src in source_tables:
        parsed = _parse_table_path(src)
        if parsed:
            _, schema, table = parsed
            source_schema_tables.append(f"{schema}.{table}")

    if not source_schema_tables:
        return _tool_error("源表路径列表为空或格式错误")

    target_catalog, target_schema, target_table_name = target_parsed
    target_schema_table = f"{target_schema}.{target_table_name}"

    try:
        result = Neo4jTableSearch.find_lineage_sql(source_schema_tables, target_schema_table)

        if not result:
            return _tool_error("未找到血缘 SQL")

        return _tool_success(
            {
                "source_tables": source_tables,
                "target_table": target_table,
                "sql_id": result.get("sql_id"),
                "sql_content": result.get("content"),
                "summary": result.get("summary"),
                "engine": result.get("engine"),
            }
        )

    except Exception as e:
        logger.error(f"get_lineage_sql 执行失败: {e}", exc_info=True)
        return _tool_error("查询失败")


# ==================== 工具列表 ====================

# List 工具（第二层）
LIST_TOOLS = [list_catalogs, list_schemas, list_tables]

# Search 工具（语义搜索）
SEARCH_TOOLS = [search_tables, search_columns]

# Detail 工具（第三层）
DETAIL_TOOLS = [get_table_detail, get_table_lineage, get_lineage_sql]

# 所有表相关工具
TABLE_TOOLS = LIST_TOOLS + SEARCH_TOOLS + DETAIL_TOOLS
