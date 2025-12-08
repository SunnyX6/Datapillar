"""
Agent 工具集
使用 LangChain 标准的 @tool 装饰器定义工具
"""

import json
from typing import Annotated, List, Optional, Any, Dict
import logging

logger = logging.getLogger(__name__)
from langchain_core.tools import tool
from pydantic import BaseModel, Field
from neo4j_graphrag.retrievers import VectorRetriever
from neo4j_graphrag.embeddings.base import Embedder

from src.core.database import Neo4jClient, MySQLClient


# ==================== 知识地图 Schema ====================

class GlobalStatistics(BaseModel):
    """全局统计信息"""
    total_atomic_metrics: int = Field(..., description="原子指标总数")
    total_derived_metrics: int = Field(..., description="派生指标总数")
    total_composite_metrics: int = Field(..., description="复合指标总数")
    total_tables: int = Field(..., description="总表数")
    total_columns: int = Field(..., description="总列数")


class TableInfo(BaseModel):
    """表信息"""
    name: str = Field(..., description="表名")
    display_name: str = Field(..., description="表显示名")
    description: str = Field(..., description="表描述")
    column_count: int = Field(..., description="该表的列数")


class SchemaInfo(BaseModel):
    """Schema 层信息"""
    layer: str = Field(..., description="层级标识（SRC/ODS/DWD/DWS）")
    name: str = Field(..., description="层级显示名")
    description: str = Field(..., description="层级描述")
    table_count: int = Field(..., description="该层表数量")
    atomic_metric_count: int = Field(..., description="该层的原子指标数")
    derived_metric_count: int = Field(..., description="该层的派生指标数")
    composite_metric_count: int = Field(..., description="该层的复合指标数")
    tables: List[TableInfo] = Field(default_factory=list, description="表列表")


class SubjectInfo(BaseModel):
    """Subject 信息"""
    name: str = Field(..., description="主题显示名")
    subject_name: str = Field(..., description="主题名称")
    description: str = Field(..., description="主题描述")
    schemas: List[SchemaInfo] = Field(default_factory=list, description="Schema 列表")


class CatalogInfo(BaseModel):
    """Catalog 信息"""
    name: str = Field(..., description="目录显示名")
    catalog_name: str = Field(..., description="目录名称")
    description: str = Field(..., description="目录描述")
    subject: SubjectInfo = Field(..., description="Subject 信息")


class BusinessHierarchy(BaseModel):
    """业务层级结构"""
    domain: str = Field(..., description="业务域显示名")
    domain_name: str = Field(..., description="业务域名称")
    description: str = Field(..., description="业务描述")
    catalog: CatalogInfo = Field(..., description="Catalog 信息")


class KnowledgeMapPayload(BaseModel):
    """知识地图返回数据结构"""
    system_instruction: str = Field(..., description="系统指令")
    statistics: GlobalStatistics = Field(..., description="全局统计信息")
    business_hierarchy: BusinessHierarchy = Field(..., description="业务层级结构")


# ==================== 统一工具返回结构 ====================

class ToolResult(BaseModel):
    """
    统一的工具返回结构

    注意：search_assets 工具不使用此结构，直接返回 kg_context JSON
    """
    status: str = Field(..., description="状态：success/error/partial")
    tool_name: str = Field(..., description="工具名称")
    data: Any = Field(default=None, description="工具返回的数据")
    message: Optional[str] = Field(None, description="可选的描述信息")
    error: Optional[str] = Field(None, description="错误信息（仅当 status=error 时）")


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


# ==================== 多模型 Embedder ====================

class MultiModelEmbedder(Embedder):
    """支持多种模型的 Embedder（GLM、OpenAI、DeepSeek）"""

    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.provider = config["provider"].lower()

    def embed_query(self, text: str) -> List[float]:
        """生成单个查询的向量嵌入"""
        if self.provider == "glm":
            from zai import ZhipuAiClient
            client = ZhipuAiClient(
                api_key=self.config["api_key"],
                base_url=self.config.get("base_url")
            )
            response = client.embeddings.create(
                model=self.config["model_name"],
                input=text
            )
            if hasattr(response, "data") and len(response.data) > 0:
                return response.data[0].embedding
            elif isinstance(response, dict) and "data" in response:
                return response["data"][0]["embedding"]
            else:
                raise ValueError(f"无法从Embedding响应中提取向量: {response}")

        elif self.provider in ["openai", "deepseek"]:
            from openai import OpenAI
            client = OpenAI(
                api_key=self.config["api_key"],
                base_url=self.config.get("base_url")
            )
            response = client.embeddings.create(
                model=self.config["model_name"],
                input=text
            )
            return response.data[0].embedding

        else:
            raise ValueError(f"不支持的Embedding模型提供商: {self.provider}")


# ==================== 全局变量 ====================

_neo4j_client: Neo4jClient = None
_mysql_client: MySQLClient = None
_embedder: MultiModelEmbedder = None
_vector_retriever: VectorRetriever = None


def init_tools(
    neo4j_client: Neo4jClient,
    mysql_client: MySQLClient = None,
    embedding_config: Dict[str, Any] = None,
):
    """初始化工具依赖"""
    global _neo4j_client, _mysql_client, _embedder, _vector_retriever
    _neo4j_client = neo4j_client
    _mysql_client = mysql_client

    if embedding_config:
        _embedder = MultiModelEmbedder(embedding_config)
        # 初始化 VectorRetriever
        _vector_retriever = VectorRetriever(
            driver=neo4j_client.driver,
            index_name="table_vector_index",
            embedder=_embedder,
            return_properties=["name", "displayName", "description"]
        )


# ==================== 工具定义 ====================

@tool(args_schema=SearchAssetsInput)
async def search_assets(query: str) -> str:
    """
    搜索数仓数据资产（向量+图混合检索）

    [功能]: 基于用户查询，使用向量相似度+图遍历混合检索，返回最相关的表、列、指标等数据资产。

    [检索策略]:
    - 向量检索：基于 embedding 语义相似度召回 Top-K 节点（Table、Column、Metric 等）
    - 图遍历：基于召回节点向上扩展业务层级（Schema → Subject → Catalog → Domain）
    - 全文检索：辅助匹配表名、字段名、描述等

    [返回内容]:
    - 匹配到的表（包含列信息、下游血缘关系、关联指标）
    - 业务层级上下文（所属 Domain/Catalog/Subject/Schema）
    - 相关性得分

    Examples:
    - User: "订单表" -> 返回包含 orders、order_detail 等表
    - User: "用户相关的表" -> 返回 user、user_profile、user_behavior 等表
    - User: "销售额指标" -> 返回关联的表和指标
    """
    if not _vector_retriever:
        logger.error("❌ VectorRetriever 未初始化，请先配置 embedding_config")
        return json.dumps({
            "status": "error",
            "message": "向量检索未配置",
            "tables": []
        }, ensure_ascii=False, indent=2)

    try:
        # Step 1: 使用 VectorRetriever 检索 Top-K 表节点
        retrieval_results = _vector_retriever.search(query_text=query, top_k=10)

        if not retrieval_results.items:
            logger.warning(f"⚠ 未找到与'{query}'相关的数据资产")
            return json.dumps({
                "status": "no_results",
                "message": f"未找到与'{query}'相关的数据资产",
                "tables": []
            }, ensure_ascii=False, indent=2)

        # Step 2: 基于召回的表节点，图遍历获取详细信息
        table_ids = [item.node.element_id for item in retrieval_results.items]

        expand_cypher = """
        UNWIND $table_ids AS table_id
        MATCH (table:Table)
        WHERE elementId(table) = table_id

        // 获取列信息
        OPTIONAL MATCH (table)-[:HAS_COLUMN]->(col:Column)

        // 获取下游血缘
        OPTIONAL MATCH (table)-[:HAS_DOWNSTREAM_LINEAGE]->(downstream:Table)

        // 获取业务层级上下文
        MATCH (table)<-[:CONTAINS]-(sch:Schema)<-[:CONTAINS]-(subj:Subject)<-[:CONTAINS]-(cat:Catalog)<-[:CONTAINS]-(dom:Domain)

        WITH table, sch, subj, cat, dom,
             collect(DISTINCT {
                 name: col.name,
                 displayName: col.displayName,
                 dataType: col.dataType,
                 description: col.description
             }) as columns,
             collect(DISTINCT downstream.name) as downstream_tables

        RETURN
            elementId(table) as table_id,
            table.name as table_name,
            table.displayName as table_display_name,
            table.description as table_description,
            columns,
            downstream_tables,
            sch.layer as schema_layer,
            sch.displayName as schema_name,
            subj.displayName as subject_name,
            cat.displayName as catalog_name,
            dom.displayName as domain_name
        """

        expanded_results = _neo4j_client.execute_query(expand_cypher, {"table_ids": table_ids})

        # Step 3: 构建返回结果（合并向量得分和图遍历详情）
        score_map = {item.node.element_id: item.score for item in retrieval_results.items}

        search_results = {
            "status": "success",
            "query": query,
            "total_results": len(expanded_results),
            "tables": []
        }

        for result in expanded_results:
            table_info = {
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
            }
            search_results["tables"].append(table_info)

        # 按相关性得分排序
        search_results["tables"].sort(key=lambda x: x["relevance_score"], reverse=True)

        logger.info(
            f"✅ [工具完成] search_assets 找到 {len(expanded_results)} 个相关表，"
            f"Top1: {search_results['tables'][0]['table_name']} (score: {search_results['tables'][0]['relevance_score']:.3f})"
        )

        return json.dumps(search_results, ensure_ascii=False, indent=2)

    except Exception as e:
        logger.error(f"❌ search_assets 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"搜索失败：{str(e)}",
            "tables": []
        }, ensure_ascii=False, indent=2)


@tool(args_schema=GetTableLineageInput)
async def get_table_lineage(source_table: str, target_table: Optional[str] = None) -> str:
    """
    获取表的详细信息和血缘关系（原子操作）

    [功能]: 查询单个源表的详细信息（列、类型、描述）及其与目标表的血缘关系。

    [返回内容]:
    - 源表的列信息（name、dataType、description）
    - 如果提供 target_table：
      * 目标表的列信息
      * 列级血缘映射（source.column → target.column）
      * 转换类型（direct、transform、aggregate）
    - 如果未提供 target_table：
      * 源表的所有下游血缘表列表

    [使用场景]:
    - 单表同步：get_table_lineage("orders", "dwd_orders")
    - 多任务：A1→B1 和 A2→B2，分别调用两次
    - 探索下游：get_table_lineage("orders") 查看所有下游表

    Examples:
    - get_table_lineage("orders", "dwd_orders") → 返回 orders → dwd_orders 的列映射
    - get_table_lineage("orders") → 返回 orders 的列信息和所有下游表
    """
    logger.info(f"🔧 [工具调用] get_table_lineage(source='{source_table}', target='{target_table}')")

    try:
        if target_table:
            # 场景1：查询 source → target 的列级血缘
            cypher = """
            MATCH (source:Table {name: $source_table})
            MATCH (target:Table {name: $target_table})

            // 获取源表的所有列（独立路径，避免笛卡尔积）
            OPTIONAL MATCH (source)-[:HAS_COLUMN]->(source_col:Column)

            // 获取目标表的所有列（独立路径）
            OPTIONAL MATCH (target)-[:HAS_COLUMN]->(target_col:Column)

            // 获取显式的列级血缘关系（只匹配存在 LINEAGE_TO 关系的列）
            OPTIONAL MATCH (source)-[:HAS_COLUMN]->(sc:Column)-[:LINEAGE_TO]->(tc:Column)<-[:HAS_COLUMN]-(target)

            RETURN
                source.name as source_table_name,
                source.displayName as source_display_name,
                source.description as source_description,
                collect(DISTINCT {
                    name: source_col.name,
                    displayName: source_col.displayName,
                    dataType: source_col.dataType,
                    description: source_col.description
                }) as source_columns,
                target.name as target_table_name,
                target.displayName as target_display_name,
                target.description as target_description,
                collect(DISTINCT {
                    name: target_col.name,
                    displayName: target_col.displayName,
                    dataType: target_col.dataType,
                    description: target_col.description
                }) as target_columns,
                collect(DISTINCT {
                    source_column: sc.name,
                    target_column: tc.name,
                    transformation_type: "direct"
                }) as column_lineage
            """

            results = _neo4j_client.execute_query(cypher, {
                "source_table": source_table,
                "target_table": target_table
            })

            if not results:
                return json.dumps({
                    "status": "not_found",
                    "message": f"未找到表 '{source_table}' 或 '{target_table}'",
                    "source_table": None,
                    "target_table": None,
                    "column_lineage": []
                }, ensure_ascii=False, indent=2)

            result = results[0]

            # 提取源列和目标列
            source_columns = [col for col in result["source_columns"] if col.get("name")]
            target_columns = [col for col in result["target_columns"] if col.get("name")]

            # 提取显式血缘映射
            explicit_lineage = [
                mapping for mapping in result.get("column_lineage", [])
                if mapping.get("source_column") and mapping.get("target_column")
            ]

            # 🔥 如果没有显式血缘关系，基于列名匹配生成默认映射
            column_lineage = explicit_lineage
            if not explicit_lineage:
                logger.info("⚠️ 未找到显式血缘关系，基于列名匹配生成默认映射")
                # 创建目标列名集合，便于快速查找
                target_col_names = {col["name"] for col in target_columns}

                # 为每个源列寻找同名目标列
                for source_col in source_columns:
                    source_name = source_col["name"]
                    if source_name in target_col_names:
                        column_lineage.append({
                            "source_column": source_name,
                            "target_column": source_name,  # 同名映射
                            "transformation_type": "direct"
                        })

            lineage_info = {
                "status": "success",
                "has_lineage": bool(explicit_lineage),  # 是否有显式血缘
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
            }

            logger.info(
                f"✅ [工具完成] get_table_lineage 找到 {len(source_columns)} 个源列，"
                f"{len(target_columns)} 个目标列，"
                f"{len(column_lineage)} 个血缘映射（{'显式' if explicit_lineage else '基于名称匹配'}）"
            )

            return json.dumps(lineage_info, ensure_ascii=False, indent=2)

        else:
            # 场景2：只查询 source 表的详细信息和下游表
            cypher = """
            MATCH (source:Table {name: $source_table})

            // 获取源表列
            OPTIONAL MATCH (source)-[:HAS_COLUMN]->(col:Column)

            // 获取下游血缘表
            OPTIONAL MATCH (source)-[:HAS_DOWNSTREAM_LINEAGE]->(downstream:Table)

            WITH source,
                 collect(DISTINCT {
                     name: col.name,
                     displayName: col.displayName,
                     dataType: col.dataType,
                     description: col.description
                 }) as columns,
                 collect(DISTINCT downstream.name) as downstream_tables

            RETURN
                source.name as table_name,
                source.displayName as display_name,
                source.description as description,
                columns,
                downstream_tables
            """

            results = _neo4j_client.execute_query(cypher, {"source_table": source_table})

            if not results:
                return json.dumps({
                    "status": "not_found",
                    "message": f"未找到表 '{source_table}'",
                    "table": None
                }, ensure_ascii=False, indent=2)

            result = results[0]

            table_info = {
                "status": "success",
                "table": {
                    "name": result["table_name"],
                    "display_name": result["display_name"],
                    "description": result["description"],
                    "columns": [col for col in result["columns"] if col.get("name")]
                },
                "downstream_tables": [
                    name for name in result.get("downstream_tables", [])
                    if name
                ]
            }

            logger.info(
                f"✅ [工具完成] get_table_lineage 找到 {len(table_info['table']['columns'])} 个列，"
                f"{len(table_info['downstream_tables'])} 个下游表"
            )

            return json.dumps(table_info, ensure_ascii=False, indent=2)

    except Exception as e:
        logger.error(f"❌ get_table_lineage 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"查询失败：{str(e)}"
        }, ensure_ascii=False, indent=2)


@tool
def list_component() -> str:
    """
    获取所有可用 ETL 组件的完整配置

    [功能]: 一次性返回数据库中所有激活组件的完整配置（包括 config_schema），供 LLM 选择使用。

    [返回内容]:
    - component_id: 组件唯一标识
    - component_name: 组件名称
    - component_type: 组件类型（ETL/SQL/SCRIPT）
    - category: 组件分类
    - description: 组件描述
    - config_schema: 配置模板（JSON Schema）
    - supported_operations: 支持的操作类型列表

    [使用场景]:
    - 生成工作流节点时，从所有组件中选择合适的组件
    - 根据操作类型（sync/transform/aggregate）筛选组件
    - 使用 config_schema 生成节点的 config 字段

    Returns:
        所有组件的完整配置列表（JSON字符串）

    Examples:
    - 从返回结果中筛选 supported_operations 包含 "sync" 的组件
    - 根据 component_id 找到对应的 config_schema
    """
    logger.info(f"🔧 [工具调用] list_component() - 返回所有组件配置")

    if not _mysql_client:
        logger.error("❌ MySQLClient 未初始化，请先调用 init_tools")
        return json.dumps({
            "status": "error",
            "message": "MySQL 客户端未初始化",
            "components": []
        }, ensure_ascii=False, indent=2)

    try:
        query = """
            SELECT
                component_id,
                component_name,
                component_type,
                category,
                description,
                config_schema,
                supported_operations
            FROM xxl_job_component
            WHERE status = 'ACTIVE'
            ORDER BY component_type, component_id
        """

        results = _mysql_client.execute_query(query)

        if not results:
            logger.warning(f"⚠️ 未找到任何可用组件")
            return json.dumps({
                "status": "error",
                "message": "未找到任何可用组件",
                "components": []
            }, ensure_ascii=False, indent=2)

        # 解析 JSON 字段
        components = []
        for row in results:
            if isinstance(row['config_schema'], str):
                row['config_schema'] = json.loads(row['config_schema'])
            if isinstance(row['supported_operations'], str):
                row['supported_operations'] = json.loads(row['supported_operations'])
            components.append(row)

        logger.info(f"✅ [工具完成] 返回 {len(components)} 个组件的完整配置")

        return json.dumps({
            "status": "success",
            "total": len(components),
            "components": components
        }, ensure_ascii=False, indent=2)

    except Exception as e:
        logger.error(f"❌ list_component 执行失败: {e}", exc_info=True)
        return json.dumps({
            "status": "error",
            "message": f"查询失败：{str(e)}",
            "components": []
        }, ensure_ascii=False, indent=2)


# ==================== 工具列表 ====================

ALL_TOOLS = [
    # 全局资产地图
    search_assets,
    # 表血缘详情
    get_table_lineage,
    # 组件配置列表
    list_component,
]


# ==================== 工具管理器 ====================

class AgentTools:
    """Agent 工具管理器"""

    def __init__(self, neo4j_client: Neo4jClient, embedding_config: Dict[str, Any] = None):
        # 初始化全局依赖
        init_tools(neo4j_client, embedding_config)

    def get_all_tools(self) -> list:
        """获取所有工具"""
        return ALL_TOOLS
