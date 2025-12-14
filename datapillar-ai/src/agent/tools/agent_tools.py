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

    返回表/列/上下文，按相关性排序。
    """
    try:
        vector_results = KnowledgeRepository.vector_search(query, top_k=10, index_name="table_vector_index")

        if not vector_results:
            return json.dumps({
                "status": "no_results",
                "message": f"未找到与'{query}'相关的数据资产",
                "tables": []
            }, ensure_ascii=False)

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
    获取企业支持的所有大数据组件列表

    返回组件ID、名称、类型、配置模板（config_schema）。
    ArchitectAgent 必须基于这些组件来设计工作流节点。
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
            components.append({
                "component_id": row.get("component_id"),
                "component_name": row.get("component_name"),
                "component_type": row.get("component_type"),
                "category": row.get("category"),
                "description": row.get("description"),
                "config_schema": row.get("config_schema"),
            })

        return json.dumps({
            "status": "success",
            "total": len(components),
            "components": components,
            "hint": "设计工作流时，每个节点的 component_id 必须是以上组件之一"
        }, ensure_ascii=False)

    except Exception as e:
        logger.error(f"list_component 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"查询失败：{str(e)}",
            "components": []
        }, ensure_ascii=False)


@tool
async def kg_join_hints(table_name: str) -> str:
    """
    获取指定表的 JOIN 线索（基于 Join 节点的 JOIN_LEFT/JOIN_RIGHT），返回 join_keys 列表。
    """
    try:
        joins = await KnowledgeRepository.get_join_hints(table_name)
        join_keys = []
        for j in joins:
            join_keys.append({
                "left_table": j.get("left_table"),
                "right_table": j.get("right_table"),
                "left_column": j.get("left_column"),
                "right_column": j.get("right_column"),
                "join_type": j.get("join_type"),
            })
        status = "success" if join_keys else "not_found"
        return json.dumps({"status": status, "join_keys": join_keys}, ensure_ascii=False)
    except Exception as exc:  # noqa: BLE001
        logger.error(f"kg_join_hints 失败: {exc}")
        return json.dumps({"status": "error", "join_keys": []}, ensure_ascii=False)


@tool
async def kg_quality_rules(table_name: str) -> str:
    """
    获取表绑定的质量规则（HAS_QUALITY_RULE），返回 dq_rules 引用。
    """
    try:
        rules = await KnowledgeRepository.get_quality_rules(table_name)
        dq_rules = []
        for r in rules:
            dq_rules.append({
                "rule": (r.get("rule_type") or "").lower(),
                "columns": [r.get("column")],
                "params": {"sql": r.get("sql_exp")},
                "severity": (r.get("severity") or "medium").lower(),
            })
        status = "success" if dq_rules else "not_found"
        return json.dumps({"status": status, "dq_rules": dq_rules}, ensure_ascii=False)
    except Exception as exc:  # noqa: BLE001
        logger.error(f"kg_quality_rules 失败: {exc}")
        return json.dumps({"status": "error", "dq_rules": []}, ensure_ascii=False)


class SearchReferenceSqlInput(BaseModel):
    """检索历史参考 SQL 的参数"""
    query: str = Field(
        ...,
        description="用户查询或需求描述，用于语义匹配相似的历史 SQL"
    )
    source_tables: Optional[list[str]] = Field(
        None,
        description="源表名列表，用于过滤（可选）"
    )
    target_tables: Optional[list[str]] = Field(
        None,
        description="目标表名列表，用于过滤（可选）"
    )
    limit: int = Field(
        default=3,
        description="返回数量，默认 3"
    )


class SearchReferenceCasesInput(BaseModel):
    """检索历史成功案例的参数"""
    source_tables: list[str] = Field(
        ...,
        description="源表名列表，用于匹配相似案例"
    )
    target_tables: list[str] = Field(
        default_factory=list,
        description="目标表名列表，用于匹配相似案例（可选）"
    )
    intent: Optional[str] = Field(
        None,
        description="ETL 意图描述，如 'join'、'aggregation'、'filter' 等"
    )
    limit: int = Field(
        default=3,
        description="返回数量，默认 3"
    )


@tool(args_schema=SearchReferenceSqlInput)
async def search_reference_sql(
    query: str,
    source_tables: Optional[list[str]] = None,
    target_tables: Optional[list[str]] = None,
    limit: int = 3,
) -> str:
    """
    检索历史成功 SQL，供 DeveloperAgent 参考。

    从知识库中检索与当前任务相似的历史成功案例 SQL。
    结果按置信度和使用次数排序，优先返回经过用户确认的高质量 SQL。

    使用场景：
    1. DeveloperAgent 生成 SQL 前，先检索是否有可复用的历史 SQL
    2. 如果找到高置信度的参考 SQL，可以直接复用或微调
    3. 如果没有找到，则从头生成

    返回字段说明：
    - fingerprint: SQL 唯一标识，用于后续更新使用次数
    - sql_text: SQL 代码
    - summary: SQL 摘要/用户原始需求
    - confidence: 置信度（0.9+ 表示用户确认过）
    - use_count: 被复用次数（越高说明越通用）
    """
    logger.info(f"search_reference_sql(query='{query[:50]}...', sources={source_tables}, targets={target_tables})")

    try:
        results = await KnowledgeRepository.search_reference_sql(
            query=query,
            source_tables=source_tables,
            target_tables=target_tables,
            limit=limit,
        )

        if not results:
            return json.dumps({
                "status": "no_results",
                "message": "未找到相关的历史参考 SQL",
                "reference_sqls": []
            }, ensure_ascii=False)

        reference_sqls = []
        for r in results:
            reference_sqls.append({
                "fingerprint": r.get("fingerprint"),
                "sql_text": r.get("sql_text"),
                "summary": r.get("summary"),
                "tags": r.get("tags") or [],
                "dialect": r.get("dialect"),
                "confidence": float(r.get("confidence") or 0.5),
                "use_count": int(r.get("use_count") or 0),
                "source_tables": r.get("source_tables") or [],
                "target_tables": r.get("target_tables") or [],
            })

        return json.dumps({
            "status": "success",
            "total_results": len(reference_sqls),
            "reference_sqls": reference_sqls,
            "hint": "如果 confidence >= 0.9 且 use_count > 0，优先考虑复用该 SQL"
        }, ensure_ascii=False)

    except Exception as e:
        logger.error(f"search_reference_sql 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"检索失败：{str(e)}",
            "reference_sqls": []
        }, ensure_ascii=False)


@tool(args_schema=SearchReferenceCasesInput)
async def search_reference_cases(
    source_tables: list[str],
    target_tables: list[str] = None,
    intent: Optional[str] = None,
    limit: int = 3,
) -> str:
    """
    检索历史成功的 ETL 案例，获取可复用的 SQL 模板。

    当用户需求涉及已有类似案例时，优先参考历史成功方案。
    返回的案例包含完整的上下文（源表、目标表、意图、SQL）。

    使用场景：
    1. KnowledgeAgent 检索知识时，同时检索相似案例
    2. 找到匹配度高的案例后，注入到 DeveloperAgent 作为 Few-shot 参考
    3. 提高 SQL 生成的准确率，减少迭代次数

    返回字段说明：
    - case_id: 案例唯一标识
    - user_query: 原始用户需求（可作为 Few-shot prompt）
    - sql_text: 成功的 SQL 代码（可作为 Few-shot response）
    - intent: ETL 意图标签
    - source_tables/target_tables: 涉及的表
    """
    from src.agent.etl_agents.memory.case_library import CaseLibrary

    logger.info(f"search_reference_cases(sources={source_tables}, targets={target_tables}, intent={intent})")

    try:
        case_library = CaseLibrary()
        cases = await case_library.search_similar_cases(
            source_tables=source_tables,
            target_tables=target_tables or [],
            intent=intent,
            limit=limit,
        )

        if not cases:
            return json.dumps({
                "status": "no_results",
                "message": "未找到相似的历史成功案例",
                "cases": []
            }, ensure_ascii=False)

        case_list = []
        for case in cases:
            case_list.append({
                "case_id": case.case_id,
                "user_query": case.user_query,
                "sql_text": case.sql_text,
                "intent": case.intent,
                "source_tables": case.source_tables,
                "target_tables": case.target_tables,
                "tags": case.tags,
            })

        return json.dumps({
            "status": "success",
            "total_results": len(case_list),
            "cases": case_list,
            "hint": "将这些案例作为 Few-shot 示例注入到 DeveloperAgent 提示词中"
        }, ensure_ascii=False)

    except Exception as e:
        logger.error(f"search_reference_cases 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"检索失败：{str(e)}",
            "cases": []
        }, ensure_ascii=False)


# ==================== 工具列表 ====================

ALL_TOOLS = [
    search_assets,
    get_table_lineage,
    list_component,
    kg_join_hints,
    kg_quality_rules,
    search_reference_sql,
    search_reference_cases,
]
