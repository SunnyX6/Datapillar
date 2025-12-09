"""
Agent 工具集
使用 LangChain 标准的 @tool 装饰器定义工具
"""

import json
from typing import Optional
import logging

logger = logging.getLogger(__name__)
from langchain_core.tools import tool
from pydantic import BaseModel, Field

from src.repositories import KnowledgeRepository, ComponentRepository


# ==================== 工具参数 Schema ====================

class SearchAssetsInput(BaseModel):
    """搜索数据资产的参数"""
    query: str = Field(
        ...,
        description="搜索关键词，用于匹配表名、列名、描述等，支持自然语言查询（如'订单表'、'用户相关的表'）"
    )


class GetTableLineageInput(BaseModel):
    """获取表血缘详情的参数（原子操作）"""
    source_table: str = Field(
        ...,
        description="源表名，如 'orders' 或 'mysql.orders'"
    )
    target_table: Optional[str] = Field(
        None,
        description="目标表名（可选），如 'dwd_orders'。如果提供，会查询源表到目标表的列级血缘和 JOIN 关系"
    )


# ==================== 工具定义 ====================

@tool(args_schema=SearchAssetsInput)
async def search_assets(query: str) -> str:
    """
    搜索数仓数据资产（向量+图混合检索）

    [功能]: 基于用户查询，使用向量相似度+图遍历混合检索，返回最相关的表、列、指标等数据资产。

    [返回内容]:
    - 匹配到的表（包含列信息、下游血缘关系）
    - 业务层级上下文（所属 Domain/Catalog/Subject/Schema）
    - 相关性得分

    Examples:
    - User: "订单表" -> 返回包含 orders、order_detail 等表
    - User: "用户相关的表" -> 返回 user、user_profile、user_behavior 等表
    """
    try:
        # Step 1: 向量检索
        vector_results = KnowledgeRepository.vector_search(query, top_k=10, index_name="table_vector_index")

        if not vector_results:
            return json.dumps({
                "status": "no_results",
                "message": f"未找到与'{query}'相关的数据资产",
                "tables": []
            }, ensure_ascii=False)

        # Step 2: 获取表详情
        table_ids = [r["element_id"] for r in vector_results]
        expanded_results = await KnowledgeRepository.search_tables_with_context(table_ids)

        score_map = {r["element_id"]: r["score"] for r in vector_results}

        tables = []
        for result in expanded_results:
            tables.append({
                "table_name": result["table_name"],
                "table_display_name": result["table_display_name"],
                "description": result["table_description"],
                "relevance_score": float(score_map.get(result["table_id"], 0.0)),
                "columns": result["columns"],
                "downstream_lineage": result["downstream_tables"],
                "business_context": {
                    "domain": result["domain_name"],
                    "catalog": result["catalog_name"],
                    "subject": result["subject_name"],
                    "schema": result["schema_name"],
                    "layer": result["schema_layer"]
                }
            })

        tables.sort(key=lambda x: x["relevance_score"], reverse=True)

        if tables:
            logger.info(f"search_assets 找到 {len(tables)} 个相关表")

        return json.dumps({
            "status": "success",
            "query": query,
            "total_results": len(tables),
            "tables": tables
        }, ensure_ascii=False)

    except Exception as e:
        logger.error(f"search_assets 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"搜索失败：{str(e)}",
            "tables": []
        }, ensure_ascii=False)


@tool(args_schema=GetTableLineageInput)
async def get_table_lineage(source_table: str, target_table: Optional[str] = None) -> str:
    """
    获取表的详细信息和血缘关系（原子操作）

    [返回内容]:
    - 源表的列信息（name、dataType、description）
    - 如果提供 target_table：列级血缘映射
    - 如果未提供 target_table：源表的所有下游血缘表列表

    Examples:
    - get_table_lineage("orders", "dwd_orders") → 返回列映射
    - get_table_lineage("orders") → 返回列信息和下游表
    """
    logger.info(f"get_table_lineage(source='{source_table}', target='{target_table}')")

    try:
        if target_table:
            result = await KnowledgeRepository.get_table_lineage_detail(source_table, target_table)

            if not result:
                return json.dumps({
                    "status": "not_found",
                    "message": f"未找到表 '{source_table}' 或 '{target_table}'"
                }, ensure_ascii=False)

            source_columns = [col for col in result["source_columns"] if col.get("name")]
            target_columns = [col for col in result["target_columns"] if col.get("name")]

            explicit_lineage = [
                m for m in result.get("column_lineage", [])
                if m.get("source_column") and m.get("target_column")
            ]

            column_lineage = explicit_lineage
            if not explicit_lineage:
                target_col_names = {col["name"] for col in target_columns}
                for src_col in source_columns:
                    if src_col["name"] in target_col_names:
                        column_lineage.append({
                            "source_column": src_col["name"],
                            "target_column": src_col["name"],
                            "transformation_type": "direct"
                        })

            return json.dumps({
                "status": "success",
                "has_lineage": bool(explicit_lineage),
                "source_table": {
                    "name": result["source_table_name"],
                    "display_name": result["source_display_name"],
                    "description": result["source_description"],
                    "columns": source_columns
                },
                "target_table": {
                    "name": result["target_table_name"],
                    "display_name": result["target_display_name"],
                    "description": result["target_description"],
                    "columns": target_columns
                },
                "column_lineage": column_lineage
            }, ensure_ascii=False)

        else:
            result = await KnowledgeRepository.get_table_detail(source_table)

            if not result:
                return json.dumps({
                    "status": "not_found",
                    "message": f"未找到表 '{source_table}'"
                }, ensure_ascii=False)

            return json.dumps({
                "status": "success",
                "table": {
                    "name": result["table_name"],
                    "display_name": result["display_name"],
                    "description": result["description"],
                    "columns": [col for col in result["columns"] if col.get("name")]
                },
                "downstream_tables": [n for n in result.get("downstream_tables", []) if n]
            }, ensure_ascii=False)

    except Exception as e:
        logger.error(f"get_table_lineage 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"查询失败：{str(e)}"
        }, ensure_ascii=False)


@tool
def list_component() -> str:
    """
    获取所有可用 ETL 组件的完整配置

    [返回内容]:
    - component_id, component_name, component_type
    - config_schema: 配置模板（JSON Schema）
    - supported_operations: 支持的操作类型列表
    """
    logger.info("list_component()")

    try:
        results = ComponentRepository.list_active_components()

        if not results:
            return json.dumps({
                "status": "error",
                "message": "未找到任何可用组件",
                "components": []
            }, ensure_ascii=False)

        components = []
        for row in results:
            if isinstance(row.get('config_schema'), str):
                row['config_schema'] = json.loads(row['config_schema'])
            if isinstance(row.get('supported_operations'), str):
                row['supported_operations'] = json.loads(row['supported_operations'])
            components.append(row)

        logger.info(f"返回 {len(components)} 个组件")

        return json.dumps({
            "status": "success",
            "total": len(components),
            "components": components
        }, ensure_ascii=False)

    except Exception as e:
        logger.error(f"list_component 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"查询失败：{str(e)}",
            "components": []
        }, ensure_ascii=False)


# ==================== 工具列表 ====================

ALL_TOOLS = [
    search_assets,
    get_table_lineage,
    list_component,
]
