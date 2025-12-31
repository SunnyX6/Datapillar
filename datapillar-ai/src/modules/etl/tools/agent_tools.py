"""
Agent 工具集

使用 LangChain 标准的 @tool 装饰器定义工具。

设计原则：
1. 全局上下文只做导航，不存储细节
2. 细节通过 Tool 按需查询
3. Tool 返回 JSON 字符串，供 Agent 解析
"""

import json
import logging

from langchain_core.tools import tool
from pydantic import BaseModel, Field

from src.infrastructure.repository import ComponentRepository, KnowledgeRepository

logger = logging.getLogger(__name__)


# ==================== 工具参数 Schema ====================


class GetTableColumnsInput(BaseModel):
    """获取表列详情的参数"""

    table_name: str = Field(
        ...,
        description="表名，支持 schema.table 或 table 格式",
    )


class GetColumnLineageInput(BaseModel):
    """获取列级血缘的参数"""

    source_table: str = Field(
        ...,
        description="源表名，格式为 schema.table",
    )
    target_table: str = Field(
        ...,
        description="目标表名，格式为 schema.table",
    )


class GetTableLineageInput(BaseModel):
    """获取表级血缘的参数"""

    table_name: str = Field(
        ...,
        description="表名，格式为 schema.table 或 table",
    )
    direction: str = Field(
        default="both",
        description="血缘方向：upstream（上游）、downstream（下游）、both（双向）",
    )


class GetSqlByLineageInput(BaseModel):
    """根据血缘精准查找历史 SQL 的参数"""

    source_tables: list[str] = Field(
        ...,
        description="源表名列表（从哪些表读数据）",
    )
    target_table: str = Field(
        ...,
        description="目标表名（写入哪个表）",
    )


class SearchAssetsInput(BaseModel):
    """搜索数据资产的参数"""

    query: str = Field(
        ...,
        description="搜索关键词，用于匹配表名、列名、描述等",
    )


# ==================== 核心工具定义 ====================


@tool(args_schema=GetTableColumnsInput)
async def get_table_columns(table_name: str) -> str:
    """
    获取表的所有列详情

    当需要了解表的具体结构（列名、类型、描述）时调用此工具。
    全局上下文只提供表的导航信息，列详情需要通过此工具按需查询。

    返回字段：
    - name: 列名
    - data_type: 数据类型
    - description: 列描述
    - nullable: 是否可空
    - tags: 列标签
    """
    logger.info(f"get_table_columns(table_name='{table_name}')")

    try:
        columns = await KnowledgeRepository.get_table_columns(table_name)

        if not columns:
            return json.dumps(
                {
                    "status": "not_found",
                    "message": f"未找到表 '{table_name}' 或该表没有列",
                    "columns": [],
                },
                ensure_ascii=False,
            )

        return json.dumps(
            {
                "status": "success",
                "table_name": table_name,
                "column_count": len(columns),
                "columns": columns,
            },
            ensure_ascii=False,
        )

    except Exception as e:
        logger.error(f"get_table_columns 执行失败: {e}", exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": f"查询失败：{str(e)}",
                "columns": [],
            },
            ensure_ascii=False,
        )


@tool(args_schema=GetColumnLineageInput)
async def get_column_lineage(source_table: str, target_table: str) -> str:
    """
    获取列级血缘映射

    当需要了解源表到目标表的字段对应关系时调用此工具。
    返回 SQL 节点及其推导出的列级映射。

    返回字段：
    - sql_id: SQL 节点 ID
    - sql_content: SQL 代码
    - column_mappings: 列映射列表
    """
    logger.info(f"get_column_lineage(source='{source_table}', target='{target_table}')")

    try:
        lineage = await KnowledgeRepository.get_column_lineage(source_table, target_table)

        if not lineage:
            return json.dumps(
                {
                    "status": "not_found",
                    "message": f"未找到 '{source_table}' 到 '{target_table}' 的列级血缘",
                    "lineage": [],
                },
                ensure_ascii=False,
            )

        return json.dumps(
            {
                "status": "success",
                "source_table": source_table,
                "target_table": target_table,
                "lineage": lineage,
            },
            ensure_ascii=False,
        )

    except Exception as e:
        logger.error(f"get_column_lineage 执行失败: {e}", exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": f"查询失败：{str(e)}",
                "lineage": [],
            },
            ensure_ascii=False,
        )


@tool(args_schema=GetTableLineageInput)
async def get_table_lineage(table_name: str, direction: str = "both") -> str:
    """
    获取表级血缘关系

    查询指定表的上下游血缘关系，帮助理解数据流向。
    ETL 开发时必须了解源表和目标表之间的血缘关系。

    返回字段：
    - upstream: 上游表列表（数据来源）
    - downstream: 下游表列表（数据去向）
    - lineage_edges: 血缘边详情
    """
    logger.info(f"get_table_lineage(table_name='{table_name}', direction='{direction}')")

    try:
        # 加载全量表级血缘
        all_lineage = await KnowledgeRepository.load_table_lineage()

        upstream = []
        downstream = []
        lineage_edges = []

        for edge in all_lineage:
            source = edge.get("source_table", "")
            target = edge.get("target_table", "")

            # 匹配表名（支持 schema.table 或 table 格式）
            is_source = source == table_name or source.endswith(f".{table_name}")
            is_target = target == table_name or target.endswith(f".{table_name}")

            if direction in ("upstream", "both") and is_target:
                upstream.append(source)
                lineage_edges.append(edge)

            if direction in ("downstream", "both") and is_source:
                downstream.append(target)
                lineage_edges.append(edge)

        # 去重
        upstream = list(set(upstream))
        downstream = list(set(downstream))

        if not upstream and not downstream:
            return json.dumps(
                {
                    "status": "not_found",
                    "message": f"未找到表 '{table_name}' 的血缘关系",
                    "upstream": [],
                    "downstream": [],
                    "lineage_edges": [],
                },
                ensure_ascii=False,
            )

        return json.dumps(
            {
                "status": "success",
                "table_name": table_name,
                "direction": direction,
                "upstream": upstream,
                "downstream": downstream,
                "lineage_edges": lineage_edges,
            },
            ensure_ascii=False,
        )

    except Exception as e:
        logger.error(f"get_table_lineage 执行失败: {e}", exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": f"查询失败：{str(e)}",
                "upstream": [],
                "downstream": [],
                "lineage_edges": [],
            },
            ensure_ascii=False,
        )


@tool(args_schema=GetSqlByLineageInput)
async def get_sql_by_lineage(source_tables: list[str], target_table: str) -> str:
    """
    根据血缘关系精准查找历史 SQL

    当你知道要从哪些源表读取数据、写入哪个目标表时，使用此工具。
    它会根据 Neo4j 中的血缘关系，找到历史上执行过相同数据流向的 SQL。

    返回的 SQL 代码包含：
    - 完整的 JOIN 条件（不需要猜测！）
    - 字段映射关系
    - 经过验证的写法风格

    优先使用此工具，而不是 search_reference_sql。
    """
    logger.info(f"get_sql_by_lineage(source={source_tables}, target={target_table})")

    try:
        sql_info = await KnowledgeRepository.get_sql_by_lineage(source_tables, target_table)

        if not sql_info:
            return json.dumps(
                {
                    "status": "not_found",
                    "message": f"未找到从 {source_tables} 到 {target_table} 的历史 SQL",
                    "sql": None,
                },
                ensure_ascii=False,
            )

        return json.dumps(
            {
                "status": "success",
                "source_tables": source_tables,
                "target_table": target_table,
                "sql_id": sql_info.get("sql_id"),
                "sql_name": sql_info.get("name"),
                "sql_content": sql_info.get("content"),  # 完整 SQL 代码，直接参考！
                "summary": sql_info.get("summary"),
                "engine": sql_info.get("engine"),
                "hint": "直接参考此 SQL 的 JOIN 条件和写法风格，不要自己猜测！",
            },
            ensure_ascii=False,
        )

    except Exception as e:
        logger.error(f"get_sql_by_lineage 执行失败: {e}", exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": f"查询失败：{str(e)}",
                "sql": None,
            },
            ensure_ascii=False,
        )


@tool(args_schema=SearchAssetsInput)
async def search_assets(query: str) -> str:
    """
    搜索数仓数据资产（向量检索）

    使用语义搜索在知识库中查找相关的表。
    返回表的基本信息及其所属的层级结构。

    返回字段：
    - table_name: 表名
    - description: 表描述
    - relevance_score: 相关性得分
    - tags: 表标签（如 layer:ODS, domain:交易）
    - schema_name: Schema 名
    - catalog_name: Catalog 名
    """
    logger.info(f"search_assets(query='{query}')")

    try:
        # 使用向量检索
        vector_results = KnowledgeRepository.vector_search(
            query, top_k=10, index_name="table_embedding"
        )

        if not vector_results:
            return json.dumps(
                {
                    "status": "no_results",
                    "message": f"未找到与 '{query}' 相关的数据资产",
                    "tables": [],
                },
                ensure_ascii=False,
            )

        # 获取表的详细上下文
        table_ids = [r["element_id"] for r in vector_results]
        expanded_results = await KnowledgeRepository.search_tables_with_context(table_ids)
        score_map = {r["element_id"]: r["score"] for r in vector_results}

        tables = []
        for result in expanded_results:
            tables.append(
                {
                    "table_name": result.get("table_name"),
                    "table_display_name": result.get("table_display_name"),
                    "description": result.get("table_description"),
                    "relevance_score": float(score_map.get(result.get("table_id"), 0.0)),
                    "column_count": len(result.get("columns", [])),
                    "schema_name": result.get("schema_name"),
                    "catalog_name": result.get("catalog_name"),
                    "layer": result.get("schema_layer"),
                }
            )

        tables.sort(key=lambda x: x["relevance_score"], reverse=True)

        return json.dumps(
            {
                "status": "success",
                "query": query,
                "total_results": len(tables),
                "tables": tables,
            },
            ensure_ascii=False,
        )

    except Exception as e:
        logger.error(f"search_assets 执行失败: {e}", exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": f"搜索失败：{str(e)}",
                "tables": [],
            },
            ensure_ascii=False,
        )


@tool
def list_component() -> str:
    """
    获取企业支持的所有大数据组件列表

    返回可用的 Job 组件（HIVE、SPARK_SQL、SHELL 等）。
    ArchitectAgent 必须基于这些组件来设计工作流节点。

    返回字段：
    - id: 组件数字 ID（设计 Job 时填充到 type_id）
    - code: 组件代码（如 HIVE、SPARK_SQL，设计 Job 时填充到 type）
    - name: 组件名称
    - type: 组件类型（SQL/SCRIPT/SYNC）
    - description: 组件描述
    - config_schema: 配置模板
    """
    logger.info("list_component()")

    try:
        results = ComponentRepository.list_active_components()

        if not results:
            return json.dumps(
                {
                    "status": "error",
                    "message": "未找到任何可用组件",
                    "components": [],
                },
                ensure_ascii=False,
            )

        components = []
        for row in results:
            # 解析 config_schema
            config_schema = row.get("config_schema")
            if isinstance(config_schema, str):
                try:
                    config_schema = json.loads(config_schema)
                except json.JSONDecodeError:
                    config_schema = {}

            components.append(
                {
                    "id": row.get("id"),
                    "code": row.get("component_code"),
                    "name": row.get("component_name"),
                    "type": row.get("component_type"),
                    "description": row.get("description"),
                    "config_schema": config_schema,
                }
            )

        return json.dumps(
            {
                "status": "success",
                "total": len(components),
                "components": components,
                "hint": "设计 Job 时，type 填组件 code，type_id 填组件 id",
            },
            ensure_ascii=False,
        )

    except Exception as e:
        logger.error(f"list_component 执行失败: {e}", exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": f"查询失败：{str(e)}",
                "components": [],
            },
            ensure_ascii=False,
        )


# ==================== 工具列表 ====================

# 核心工具：按需查询细节
DETAIL_TOOLS = [
    get_table_columns,
    get_column_lineage,
    get_table_lineage,
    get_sql_by_lineage,  # 根据血缘精准匹配历史 SQL
]

# 搜索工具：发现相关资产
SEARCH_TOOLS = [
    search_assets,
]

# 组件工具：获取可用组件
COMPONENT_TOOLS = [
    list_component,
]

# 所有工具
ALL_TOOLS = DETAIL_TOOLS + SEARCH_TOOLS + COMPONENT_TOOLS
