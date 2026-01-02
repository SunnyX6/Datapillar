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
import time
from typing import Any

import httpx
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


class GetColumnValueDomainInput(BaseModel):
    """获取列关联值域的参数"""

    column_element_id: str = Field(
        ...,
        description="列节点的 Neo4j elementId(column)",
    )


# ==================== 核心工具定义 ====================


class GetCatalogSchemaNavInput(BaseModel):
    """获取 Catalog -> Schema 导航的参数（导航级，不含表明细）"""

    limit: int = Field(default=2000, ge=1, le=2000, description="返回 Catalog 数量限制")


class GetTagNavInput(BaseModel):
    """获取 tag 导航的参数（导航级，不含 element_id）"""

    limit_tags: int = Field(default=12, ge=1, le=50, description="返回 tag 数量限制")
    tables_per_tag: int = Field(default=8, ge=1, le=30, description="每个 tag 返回样例表数量")


@tool(args_schema=GetCatalogSchemaNavInput)
async def get_catalog_schema_nav(limit: int = 2000) -> str:
    """
    获取 Catalog -> Schema 导航（导航级）

    用途：
    - no-hit 引导时告诉用户“知识库里大概有哪些库/Schema”
    约束：
    - 禁止返回表明细/指针/element_id
    """
    logger.info("get_catalog_schema_nav(limit=%s)", limit)
    try:
        nav = await KnowledgeRepository.load_catalog_schema_nav()
        if not nav:
            return json.dumps(
                {"status": "no_results", "catalog_schema_nav": []},
                ensure_ascii=False,
            )
        return json.dumps(
            {"status": "success", "catalog_schema_nav": nav[:limit]},
            ensure_ascii=False,
        )
    except Exception as e:
        logger.error("get_catalog_schema_nav 执行失败: %s", e, exc_info=True)
        return json.dumps(
            {"status": "error", "message": str(e), "catalog_schema_nav": []},
            ensure_ascii=False,
        )


@tool(args_schema=GetTagNavInput)
async def get_tag_nav(limit_tags: int = 12, tables_per_tag: int = 8) -> str:
    """
    获取 tag 导航（导航级）

    用途：
    - no-hit 引导时基于自由标签（例如 ods、交易域）给出“系统能做什么”的推荐入口
    约束：
    - 返回样例表仅包含 table_name/display_name/description/tags 等导航信息
    - 禁止返回指针/element_id
    """
    logger.info("get_tag_nav(limit_tags=%s, tables_per_tag=%s)", limit_tags, tables_per_tag)
    try:
        nav = await KnowledgeRepository.load_tag_nav(
            limit_tags=limit_tags,
            tables_per_tag=tables_per_tag,
        )
        if not nav:
            return json.dumps(
                {"status": "no_results", "tag_nav": []},
                ensure_ascii=False,
            )
        return json.dumps(
            {"status": "success", "tag_nav": nav},
            ensure_ascii=False,
        )
    except Exception as e:
        logger.error("get_tag_nav 执行失败: %s", e, exc_info=True)
        return json.dumps(
            {"status": "error", "message": str(e), "tag_nav": []},
            ensure_ascii=False,
        )


class RecommendGuidanceInput(BaseModel):
    user_query: str = Field(..., description="用户输入（自由文本）")
    limit_tags: int = Field(default=12, ge=1, le=50, description="返回 tag 数量限制")
    tables_per_tag: int = Field(default=8, ge=1, le=30, description="每个 tag 返回样例表数量")
    catalog_limit: int = Field(default=2000, ge=1, le=2000, description="返回 catalog 数量限制")


@tool(args_schema=RecommendGuidanceInput)
async def recommend_guidance(
    user_query: str,
    limit_tags: int = 12,
    tables_per_tag: int = 8,
    catalog_limit: int = 2000,
) -> str:
    """
    推荐引导（ETL 场景）

    说明：
    - 这是“推荐引导数据”工具：只负责聚合知识库导航（tag/catalog），不给用户生成文案
    - 面向用户的引导文案必须由 KnowledgeAgent 生成（工具层不调用 LLM）
    - 禁止输出 element_id / node_id（避免把知识指针暴露给用户）
    """
    logger.info(
        "recommend_guidance(query='%s', limit_tags=%s, tables_per_tag=%s, catalog_limit=%s)",
        user_query,
        limit_tags,
        tables_per_tag,
        catalog_limit,
    )
    try:
        catalog_nav = await KnowledgeRepository.load_catalog_schema_nav()
        tag_nav = await KnowledgeRepository.load_tag_nav(limit_tags=limit_tags, tables_per_tag=tables_per_tag)
        return json.dumps(
            {
                "status": "success",
                "user_query": user_query,
                "catalog_schema_nav": (catalog_nav or [])[:catalog_limit],
                "tag_nav": tag_nav or [],
            },
            ensure_ascii=False,
        )
    except Exception as e:
        logger.error("recommend_guidance 执行失败: %s", e, exc_info=True)
        return json.dumps(
            {"status": "error", "message": str(e), "user_query": user_query, "catalog_schema_nav": [], "tag_nav": []},
            ensure_ascii=False,
        )


class SearchKnowledgeNodesInput(BaseModel):
    """统一检索 Knowledge 节点的参数（返回 element_id，可用于生成 ETLPointer）"""

    query: str = Field(..., description="检索 query（自由文本）")
    top_k: int = Field(default=12, ge=1, le=50, description="召回数量")
    min_score: float = Field(default=0.8, ge=0.0, le=1.0, description="最低相关性阈值")


class ResolveDocPointerInput(BaseModel):
    """
    解析 DocPointer 的参数

    说明：
    - provider/ref 组合定义“指向哪里”，不做强制结构约束
    - 解析结果必须返回可引用证据（content + source 元信息）
    """

    provider: str = Field(..., description="文档指针提供方（例如 url/gitlab/vectordb 等）")
    ref: dict[str, Any] = Field(default_factory=dict, description="不透明引用（由 provider 自行定义）")


@tool(args_schema=SearchKnowledgeNodesInput)
async def search_knowledge_nodes(query: str, top_k: int = 12, min_score: float = 0.8) -> str:
    """
    统一检索 Knowledge 节点（返回可验证 element_id）

    说明：
    - 该工具返回的是“候选节点（含 element_id）”，不是指针（ETLPointer）与明细
    - KnowledgeAgent 会基于返回结果组装严格 ETLPointer
    """
    logger.info("search_knowledge_nodes(query='%s', top_k=%s, min_score=%s)", query, top_k, min_score)
    try:
        raw_nodes = KnowledgeRepository.search_knowledge_nodes_with_context(
            query=query,
            top_k=top_k,
            min_score=min_score,
        )
        return json.dumps(
            {"status": "success", "query": query, "total": len(raw_nodes or []), "nodes": raw_nodes or []},
            ensure_ascii=False,
        )
    except Exception as e:
        logger.error("search_knowledge_nodes 执行失败: %s", e, exc_info=True)
        return json.dumps(
            {"status": "error", "message": str(e), "query": query, "total": 0, "nodes": []},
            ensure_ascii=False,
        )


async def _fetch_url_text(url: str, *, timeout_seconds: int = 10) -> str:
    async with httpx.AsyncClient(
        timeout=timeout_seconds,
        follow_redirects=True,
        headers={
            "User-Agent": "DatapillarAI/etl-agent",
            "Accept": "text/plain, text/markdown, application/json, */*",
        },
    ) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        return resp.text


def _apply_span(text: str, span: object) -> str:
    if not isinstance(span, dict):
        return text
    start = span.get("start")
    end = span.get("end")
    if isinstance(start, int) and start < 0:
        start = 0
    if isinstance(end, int) and end < 0:
        end = 0
    if not isinstance(start, int):
        start = 0
    if not isinstance(end, int):
        end = None
    return text[start:end]


def _truncate(text: str, *, max_chars: int) -> tuple[str, bool]:
    if max_chars <= 0:
        return "", True
    if len(text) <= max_chars:
        return text, False
    return text[:max_chars], True


@tool(args_schema=ResolveDocPointerInput)
async def resolve_doc_pointer(provider: str, ref: dict[str, Any]) -> str:
    """
    解析文档/规范指针为可引用证据

    返回：
    - status: success|error
    - source: {provider, ref}
    - content: 证据内容（可能截断）
    - retrieved_at_ms: 拉取时间
    """
    provider_norm = (provider or "").strip().lower()
    ref = ref or {}
    max_chars = ref.get("max_chars")
    if not isinstance(max_chars, int):
        max_chars = 4000

    try:
        if provider_norm in {"inline", "text"}:
            content = ref.get("content")
            if not isinstance(content, str) or not content.strip():
                return json.dumps(
                    {
                        "status": "error",
                        "message": "inline/text 指针缺少 ref.content",
                        "source": {"provider": provider, "ref": ref},
                    },
                    ensure_ascii=False,
                )
            content = _apply_span(content, ref.get("span"))
            content, truncated = _truncate(content, max_chars=max_chars)
            return json.dumps(
                {
                    "status": "success",
                    "source": {"provider": provider, "ref": ref},
                    "content": content,
                    "content_length": len(content),
                    "truncated": truncated,
                    "retrieved_at_ms": int(time.time() * 1000),
                },
                ensure_ascii=False,
            )

        if provider_norm in {"url", "http", "https", "gitlab"}:
            url = ref.get("url") or ref.get("raw_url") or ref.get("source_url")
            if not isinstance(url, str) or not url.strip():
                return json.dumps(
                    {
                        "status": "error",
                        "message": "url 指针缺少 ref.url（或 raw_url/source_url）",
                        "source": {"provider": provider, "ref": ref},
                    },
                    ensure_ascii=False,
                )
            if not (url.startswith("http://") or url.startswith("https://")):
                return json.dumps(
                    {
                        "status": "error",
                        "message": "仅支持 http/https URL",
                        "source": {"provider": provider, "ref": ref},
                    },
                    ensure_ascii=False,
                )
            text = await _fetch_url_text(url, timeout_seconds=10)
            text = _apply_span(text, ref.get("span"))
            content, truncated = _truncate(text, max_chars=max_chars)
            return json.dumps(
                {
                    "status": "success",
                    "source": {"provider": provider, "ref": ref},
                    "content": content,
                    "content_length": len(content),
                    "truncated": truncated,
                    "retrieved_at_ms": int(time.time() * 1000),
                },
                ensure_ascii=False,
            )

        return json.dumps(
            {
                "status": "error",
                "message": f"不支持的 provider: {provider}",
                "source": {"provider": provider, "ref": ref},
            },
            ensure_ascii=False,
        )
    except httpx.HTTPError as e:
        return json.dumps(
            {
                "status": "error",
                "message": f"文档解析失败: {str(e)}",
                "source": {"provider": provider, "ref": ref},
            },
            ensure_ascii=False,
        )
    except Exception as e:
        logger.error("resolve_doc_pointer 执行失败: %s", e, exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": str(e),
                "source": {"provider": provider, "ref": ref},
            },
            ensure_ascii=False,
        )


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


def _parse_value_domain_items(items: object) -> list[dict]:
    """
    将 ValueDomain.items 解析为枚举项列表

    支持：
    - Neo4j list（字符串/对象）
    - JSON 字符串（数组/对象）
    - 逗号分隔字符串：value:label,value2:label2
    """
    if items is None:
        return []

    def parse_kv(text: str) -> dict | None:
        t = (text or "").strip()
        if not t:
            return None
        if ":" in t:
            value, label = t.split(":", 1)
            value = value.strip()
            label = (label or "").strip()
            return {"value": value, "label": label or value}
        return {"value": t, "label": t}

    if isinstance(items, list):
        out: list[dict] = []
        for it in items:
            if isinstance(it, dict):
                out.append(it)
            elif isinstance(it, str):
                kv = parse_kv(it)
                if kv:
                    out.append(kv)
        return out

    if isinstance(items, str):
        raw = items.strip()
        if not raw:
            return []
        if raw.startswith("[") or raw.startswith("{"):
            try:
                parsed = json.loads(raw)
                if isinstance(parsed, list):
                    return _parse_value_domain_items(parsed)
                if isinstance(parsed, dict):
                    return [parsed]
            except json.JSONDecodeError:
                pass

        parts = [p.strip() for p in raw.split(",") if p.strip()]
        out2: list[dict] = []
        for p in parts:
            kv = parse_kv(p)
            if kv:
                out2.append(kv)
        return out2

    return []


@tool(args_schema=GetColumnValueDomainInput)
async def get_column_value_domain(column_element_id: str) -> str:
    """
    获取列关联的值域（ValueDomain）

    用途：
    - 将自然语言取值（如“未支付/退款”）映射到值域枚举的真实取值
    """
    logger.info(f"get_column_value_domain(column_element_id='{column_element_id}')")

    try:
        payload = await KnowledgeRepository.get_column_value_domains_by_element_id(column_element_id)
        if not payload:
            return json.dumps(
                {
                    "status": "not_found",
                    "message": f"未找到列(element_id={column_element_id})或无法读取值域关系",
                    "column_element_id": column_element_id,
                    "value_domains": [],
                },
                ensure_ascii=False,
            )

        value_domains = payload.get("value_domains") or []
        normalized = []
        for vd in value_domains:
            if not isinstance(vd, dict):
                continue
            normalized.append(
                {
                    "element_id": vd.get("element_id"),
                    "domain_code": vd.get("domain_code"),
                    "domain_name": vd.get("domain_name"),
                    "domain_type": vd.get("domain_type"),
                    "domain_level": vd.get("domain_level"),
                    "data_type": vd.get("data_type"),
                    "description": vd.get("description"),
                    "items": _parse_value_domain_items(vd.get("items")),
                    "raw_items": vd.get("items"),
                }
            )

        return json.dumps(
            {
                "status": "success",
                "column_element_id": payload.get("column_element_id") or column_element_id,
                "column_id": payload.get("column_id"),
                "column_name": payload.get("column_name"),
                "schema_name": payload.get("schema_name"),
                "table_name": payload.get("table_name"),
                "value_domain_count": len(normalized),
                "value_domains": normalized,
            },
            ensure_ascii=False,
        )
    except Exception as e:
        logger.error(f"get_column_value_domain 执行失败: {e}", exc_info=True)
        return json.dumps(
            {
                "status": "error",
                "message": f"查询失败：{str(e)}",
                "column_element_id": column_element_id,
                "value_domains": [],
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

    使用统一检索在知识库中查找相关的 Knowledge 节点（表/列/值域/指标等），最终仍返回可验证的 element_id。

    兼容输出：
    - nodes：通用候选节点列表（任意 Knowledge 节点）
    - tables：从 nodes 中派生的表列表（便于下游仍以表为入口）

    返回字段：
    - nodes: [{element_id, labels, primary_label, qualified_name, path, name, code, score, catalog_name, schema_name, table_name}]
    - tables: [{table_id, element_id, table_name, table_display_name, description, relevance_score, tags, schema_name, catalog_name, layer, column_count}]
    """
    logger.info(f"search_assets(query='{query}')")

    try:
        raw_nodes = KnowledgeRepository.search_knowledge_nodes_with_context(
            query=query,
            top_k=12,
            min_score=0.8,
        )

        if not raw_nodes:
            return json.dumps(
                {
                    "status": "no_results",
                    "message": f"未找到与 '{query}' 相关的数据资产",
                    "query": query,
                    "total_nodes": 0,
                    "nodes": [],
                    "total_results": 0,
                    "tables": [],
                },
                ensure_ascii=False,
            )

        nodes = []
        score_map: dict[str, float] = {}
        table_candidates: list[str] = []
        for n in raw_nodes:
            element_id = n.get("element_id")
            if not element_id:
                continue
            score = float(n.get("score") or 0.0)
            node_id = n.get("node_id")
            labels = n.get("labels") or []
            primary_label = n.get("primary_label")

            score_map[element_id] = score
            if node_id:
                score_map[str(node_id)] = score

            nodes.append(
                {
                    "element_id": element_id,
                    "labels": labels,
                    "primary_label": primary_label,
                    "qualified_name": n.get("qualified_name"),
                    "path": n.get("path"),
                    "name": n.get("name"),
                    "code": n.get("code"),
                    "score": score,
                    "catalog_name": n.get("catalog_name"),
                    "schema_name": n.get("schema_name"),
                    "table_name": n.get("table_name"),
                }
            )

            if "Table" in set(labels or []):
                table_candidates.append(str(node_id or element_id))

        tables: list[dict] = []
        if table_candidates:
            expanded_results = await KnowledgeRepository.search_tables_with_context(table_candidates)
            for result in expanded_results:
                schema_layer_tag = result.get("schema_layer_tag") or ""
                layer = None
                if isinstance(schema_layer_tag, str) and schema_layer_tag.startswith("layer:"):
                    layer = schema_layer_tag.split(":", 1)[1] or None

                tables.append(
                    {
                        "table_id": result.get("table_id"),
                        "element_id": result.get("element_id"),
                        "table_name": result.get("table_name"),
                        "table_display_name": result.get("table_display_name"),
                        "description": result.get("table_description"),
                        "relevance_score": float(
                            score_map.get(str(result.get("table_id")))
                            or score_map.get(str(result.get("element_id")))
                            or 0.0
                        ),
                        "column_count": int(result.get("column_count", 0) or 0),
                        "schema_name": result.get("schema_name"),
                        "catalog_name": result.get("catalog_name"),
                        "layer": layer,
                        "tags": result.get("table_tags") or [],
                    }
                )

            tables.sort(key=lambda x: x["relevance_score"], reverse=True)

        nodes.sort(key=lambda x: x.get("score", 0), reverse=True)

        return json.dumps(
            {
                "status": "success",
                "query": query,
                "total_nodes": len(nodes),
                "nodes": nodes,
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
                "query": query,
                "total_nodes": 0,
                "nodes": [],
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
    get_column_value_domain,
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
