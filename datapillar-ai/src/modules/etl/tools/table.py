"""
知识库表相关的工具

工具列表：
- get_table_detail: 获取表详情（含列和值域）
- get_table_lineage: 获取表血缘关系
- get_lineage_sql: 根据血缘关系精准查找历史 SQL

设计原则：
- 工具接受精确的定位参数（catalog/schema/table），由指针提供
- 直接查询，不做模糊搜索
- 每个工具返回自包含的结果
"""

import json
import logging

from langchain_core.tools import tool
from pydantic import BaseModel, Field

from src.infrastructure.repository.kg import Neo4jTableSearch

logger = logging.getLogger(__name__)


# ==================== 工具参数 Schema ====================


class GetTableDetailInput(BaseModel):
    """获取表详情的参数"""

    catalog: str = Field(..., description="Catalog 名称")
    schema_name: str = Field(..., description="Schema 名称")
    table: str = Field(..., description="表名")


class GetTableLineageInput(BaseModel):
    """获取表级血缘的参数"""

    catalog: str = Field(..., description="Catalog 名称")
    schema_name: str = Field(..., description="Schema 名称")
    table: str = Field(..., description="表名")
    direction: str = Field(
        default="both",
        description="血缘方向：upstream（上游）、downstream（下游）、both（双向）",
    )


class GetLineageSqlInput(BaseModel):
    """根据血缘精准查找历史 SQL 的参数"""

    source_tables: list[str] = Field(
        ...,
        description="源表路径列表，格式：schema.table（如 ['ods_20231201.t_ord_main', 'ods_20231201.t_user_info']）",
    )
    target_table: str = Field(
        ...,
        description="目标表路径，格式：schema.table（如 'dw_core.order_detail_clean'）",
    )


# ==================== 内部辅助函数 ====================


def _tool_error(message: str, **extra: object) -> str:
    """构造工具错误响应"""
    payload: dict[str, object] = {"status": "error", "message": message}
    payload.update(extra)
    return json.dumps(payload, ensure_ascii=False)


def _tool_success(data: dict) -> str:
    """构造工具成功响应"""
    return json.dumps({"status": "success", **data}, ensure_ascii=False)


# ==================== 工具定义 ====================


@tool("get_table_detail", args_schema=GetTableDetailInput)
def get_table_detail(catalog: str, schema_name: str, table: str) -> str:
    """
    获取表详情（含列和值域）

    通过精确的 catalog/schema/table 路径直接查询表的完整结构信息。

    返回字段：
    - status: "success" | "error"
    - catalog/schema/table/description/columns

    输入示例：
    {"catalog": "hive_prod", "schema_name": "ods_20231201", "table": "t_ord_main"}

    输出示例（成功）：
    {
        "status": "success",
        "catalog": "hive_prod",
        "schema": "ods_20231201",
        "table": "t_ord_main",
        "description": "订单主表",
        "columns": [
            {"name": "ord_sts", "dataType": "varchar", "valueDomain": {"code": "ORDER_STATUS", "items": [...]}}
        ]
    }
    """
    logger.info(f"get_table_detail(catalog='{catalog}', schema='{schema_name}', table='{table}')")

    try:
        detail = Neo4jTableSearch.get_table_detail(catalog, schema_name, table)

        if not detail:
            return _tool_error(f"未找到表: {catalog}.{schema_name}.{table}")

        return _tool_success(detail)

    except Exception as e:
        logger.error(f"get_table_detail 执行失败: {e}", exc_info=True)
        return _tool_error(str(e))


@tool("get_table_lineage", args_schema=GetTableLineageInput)
def get_table_lineage(catalog: str, schema_name: str, table: str, direction: str = "both") -> str:
    """
    获取表血缘关系

    通过精确的 catalog/schema/table 路径查询表的上下游血缘关系。

    返回字段：
    - status: "success" | "error"
    - catalog/schema/table/direction
    - upstream: 上游表列表
    - downstream: 下游表列表
    - edges: 血缘边信息

    输入示例：
    {"catalog": "hive_prod", "schema_name": "dw_core", "table": "order_detail_clean", "direction": "upstream"}

    输出示例（成功）：
    {
        "status": "success",
        "catalog": "hive_prod",
        "schema": "dw_core",
        "table": "order_detail_clean",
        "direction": "upstream",
        "upstream": ["ods_20231201.t_ord_main", "ods_20231201.t_user_info"],
        "downstream": [],
        "edges": [...]
    }
    """
    logger.info(
        f"get_table_lineage(catalog='{catalog}', schema='{schema_name}', table='{table}', direction='{direction}')"
    )

    try:
        lineage = Neo4jTableSearch.get_table_lineage(schema_name, table, direction)

        if not lineage.get("upstream") and not lineage.get("downstream"):
            return _tool_error(f"未找到表 {catalog}.{schema_name}.{table} 的血缘关系")

        return _tool_success(
            {
                "catalog": catalog,
                "schema": schema_name,
                "table": table,
                "direction": direction,
                **lineage,
            }
        )

    except Exception as e:
        logger.error(f"get_table_lineage 执行失败: {e}", exc_info=True)
        return _tool_error(str(e))


@tool("get_lineage_sql", args_schema=GetLineageSqlInput)
def get_lineage_sql(source_tables: list[str], target_table: str) -> str:
    """
    根据血缘关系精准查找历史 SQL

    通过精确的源表和目标表路径查找历史上执行过相同数据流向的 SQL。

    返回字段：
    - status: "success" | "error"
    - source_tables: 源表路径列表
    - target_table: 目标表路径
    - sql_id/sql_content/summary/engine

    输入示例：
    {
        "source_tables": ["ods_20231201.t_ord_main", "ods_20231201.t_user_info"],
        "target_table": "dw_core.order_detail_clean"
    }

    输出示例（成功）：
    {
        "status": "success",
        "source_tables": ["ods_20231201.t_ord_main", "ods_20231201.t_user_info"],
        "target_table": "dw_core.order_detail_clean",
        "sql_id": "abc123",
        "sql_content": "INSERT INTO ...",
        "summary": "从订单主表和用户表清洗订单明细",
        "engine": "spark"
    }
    """
    logger.info(f"get_lineage_sql(source={source_tables}, target='{target_table}')")

    try:
        # 直接使用传入的精确路径
        result = Neo4jTableSearch.find_lineage_sql(source_tables, target_table)

        if not result:
            return _tool_error(f"未找到从 {source_tables} 到 {target_table} 的历史 SQL")

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
        return _tool_error(str(e))


# ==================== 工具列表 ====================

TABLE_TOOLS = [
    get_table_detail,
    get_table_lineage,
    get_lineage_sql,
]
