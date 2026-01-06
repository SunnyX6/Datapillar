"""
Knowledge Agent（知识服务）

定位：
- 提供统一的全局知识检索服务
- 一次检索返回所有相关知识（Table、Column、ValueDomain、Tag 等）
- 根据指针的 tools 字段告诉调用方可用哪些工具
- 管理 Agent 工具权限

设计原则：
- 全局检索：不限制 node_types，返回所有相关知识
- 指针是"指路"，不是"明细"：不输出列/SQL/全文等大字段
- 严格可验证：资产类指针必须包含 Neo4j element_id
- 工具由指针驱动：指针的 tools 字段决定可用工具

注意：
- KnowledgeAgent 不是真正的 Agent，只是图里的一个节点
- 直接调用 Neo4jNodeSearch 进行检索，不走 tool
"""

import logging
from typing import Any

from pydantic import BaseModel, Field

from src.modules.etl.schemas.agent_result import AgentResult

logger = logging.getLogger(__name__)


# ==================== Agent 类型常量 ====================


class AgentType:
    """Agent 类型"""

    ANALYST = "analyst"
    ARCHITECT = "architect"
    DEVELOPER = "developer"
    REVIEWER = "reviewer"


# ==================== Agent 工具权限配置 ====================


AGENT_TOOLS_MAP: dict[str, list[str]] = {
    AgentType.ANALYST: [
        "get_table_detail",
    ],
    AgentType.ARCHITECT: [
        "get_table_lineage",
        "list_component",
    ],
    AgentType.DEVELOPER: [
        "get_table_detail",
        "get_column_valuedomain",
        "get_table_lineage",
        "get_lineage_sql",
    ],
    AgentType.REVIEWER: [
        "get_table_detail",
        "get_column_valuedomain",
    ],
}


def get_agent_tools(agent_type: str) -> list[str]:
    """获取 Agent 的工具权限列表"""
    return AGENT_TOOLS_MAP.get(agent_type, [])


# ==================== 指针数据结构 ====================


class TablePointer(BaseModel):
    """表指针"""

    catalog: str = Field(..., description="Catalog 名称")
    schema_name: str = Field(..., description="Schema 名称")
    table: str = Field(..., description="表名")
    description: str | None = Field(default=None, description="表描述")
    score: float | None = Field(default=None, description="检索得分")
    tools: list[str] = Field(default_factory=list, description="可用工具列表")

    model_config = {"extra": "ignore"}


class ColumnPointer(BaseModel):
    """列指针"""

    catalog: str = Field(..., description="Catalog 名称")
    schema_name: str = Field(..., description="Schema 名称")
    table: str = Field(..., description="所属表名")
    column: str = Field(..., description="列名")
    data_type: str | None = Field(default=None, description="数据类型")
    description: str | None = Field(default=None, description="列描述")
    valuedomain_code: str | None = Field(default=None, description="关联值域编码")
    score: float | None = Field(default=None, description="检索得分")
    tools: list[str] = Field(default_factory=list, description="可用工具列表")

    model_config = {"extra": "ignore"}


class ValueDomainPointer(BaseModel):
    """值域指针（直接内联值，无需调用工具）"""

    code: str = Field(..., description="值域编码")
    name: str = Field(..., description="值域名称")
    domain_type: str | None = Field(default=None, description="值域类型：ENUM/RANGE/REGEX")
    values: list[str] = Field(default_factory=list, description="枚举值列表，格式：VALUE=显示名")
    description: str | None = Field(default=None, description="值域描述")
    score: float | None = Field(default=None, description="检索得分")
    tools: list[str] = Field(default_factory=list, description="可用工具列表（通常为空）")

    model_config = {"extra": "ignore"}


class SqlPointer(BaseModel):
    """SQL 指针"""

    sql_id: str = Field(..., description="SQL ID")
    summary: str | None = Field(default=None, description="SQL 摘要")
    source_tables: list[str] = Field(default_factory=list, description="源表列表")
    target_table: str | None = Field(default=None, description="目标表")
    score: float | None = Field(default=None, description="检索得分")
    tools: list[str] = Field(default_factory=list, description="可用工具列表")

    model_config = {"extra": "ignore"}


class KnowledgeContext(BaseModel):
    """
    知识上下文（分类返回）

    设计理念：
    - 指针是"索引卡"：告诉调用方资产在哪里
    - tools 是"钥匙"：告诉调用方可以用什么工具获取详情
    - 导航信息共享，钥匙需要权限
    """

    tables: list[TablePointer] = Field(default_factory=list, description="表指针列表")
    columns: list[ColumnPointer] = Field(default_factory=list, description="列指针列表")
    valuedomains: list[ValueDomainPointer] = Field(default_factory=list, description="值域指针列表")
    sqls: list[SqlPointer] = Field(default_factory=list, description="SQL 指针列表")

    model_config = {"extra": "ignore"}

    def to_llm_context(self, allowlist: list[str] | None = None) -> dict[str, Any] | None:
        """
        序列化为 LLM 可用的上下文格式。

        Args:
            allowlist: 员工的权限列表（可用工具）。
                       如果提供，指针的 tools 会过滤为 tools ∩ allowlist

        设计理念：
        - 导航信息（catalog/schema/table）是共享的，所有员工都能看
        - tools 字段是"钥匙"，过滤为员工有权限的部分
        - 值域自包含（tools=[]），无需钥匙也能用

        Returns:
            {"knowledge_context": {...}} 或 None（无任何指针时）
        """
        result: dict[str, Any] = {}
        allowlist_set = set(allowlist) if allowlist else None

        for field_name in ["tables", "columns", "valuedomains", "sqls"]:
            pointers = getattr(self, field_name, [])
            if not pointers:
                continue

            items = []
            for p in pointers:
                item = p.model_dump()
                # 过滤 tools：只保留员工有权限的钥匙
                if allowlist_set and item.get("tools"):
                    item["tools"] = [t for t in item["tools"] if t in allowlist_set]
                items.append(item)

            result[field_name] = items

        return {"knowledge_context": result} if result else None

    def summary(self) -> str:
        """返回简短摘要（用于日志）"""
        parts = []
        if self.tables:
            parts.append(f"{len(self.tables)} 表")
        if self.columns:
            parts.append(f"{len(self.columns)} 列")
        if self.valuedomains:
            parts.append(f"{len(self.valuedomains)} 值域")
        if self.sqls:
            parts.append(f"{len(self.sqls)} SQL")
        return ", ".join(parts) if parts else "无"

    def is_empty(self) -> bool:
        """是否没有任何指针"""
        return not any([self.tables, self.columns, self.valuedomains, self.sqls])


class KnowledgeAgent:
    """
    知识检索服务

    核心方法：
    - global_search(): 全局检索，返回分类的知识上下文
    - run(): 图节点执行（初始检索）

    设计理念：
    - 一次全局检索，返回所有相关知识（Table、Column、ValueDomain 等）
    - 按类型分类返回，每个指针自带可用工具列表
    - 值域直接内联值，无需调用工具
    """

    def __init__(self, *, max_pointers: int = 20, min_score: float = 0.75):
        self.max_pointers = max(1, min(int(max_pointers), 50))
        self.min_score = float(min_score)

    async def global_search(
        self,
        query: str,
        top_k: int | None = None,
        min_score: float | None = None,
    ) -> KnowledgeContext:
        """
        全局知识检索（核心方法）

        返回分类的知识上下文，包含 tables/columns/valuedomains/sqls
        """
        from src.infrastructure.repository.kg.search_node import Neo4jNodeSearch

        actual_top_k = top_k if top_k is not None else self.max_pointers
        actual_min_score = min_score if min_score is not None else self.min_score

        logger.info(
            "🔍 global_search(query='%s', top_k=%s, min_score=%s)",
            query[:50] if query else "",
            actual_top_k,
            actual_min_score,
        )

        # 混合检索召回
        hits = Neo4jNodeSearch.hybrid_search(
            query=query,
            top_k=actual_top_k,
            min_score=actual_min_score,
        )

        if not hits:
            logger.info("⚠️ global_search 召回为空")
            return KnowledgeContext()

        # 提取 node_id 和 score
        node_ids = [hit.node_id for hit in hits]
        score_map = {hit.node_id: hit.score for hit in hits}

        # 获取节点上下文
        context_list = Neo4jNodeSearch.get_nodes_context(node_ids)

        # 合并 score
        for item in context_list:
            node_id = item.get("node_id")
            item["score"] = score_map.get(node_id, 0.0)

        # 构建分类指针
        knowledge_ctx = self._build_knowledge_context(context_list)

        logger.info(
            "✅ global_search 返回: tables=%d, columns=%d, valuedomains=%d, sqls=%d",
            len(knowledge_ctx.tables),
            len(knowledge_ctx.columns),
            len(knowledge_ctx.valuedomains),
            len(knowledge_ctx.sqls),
        )

        return knowledge_ctx

    def get_available_tools(self, ctx: KnowledgeContext) -> set[str]:
        """从知识上下文获取所有可用工具"""
        tools: set[str] = set()
        for t in ctx.tables:
            tools.update(t.tools)
        for c in ctx.columns:
            tools.update(c.tools)
        for v in ctx.valuedomains:
            tools.update(v.tools)
        for s in ctx.sqls:
            tools.update(s.tools)
        return tools

    async def run(
        self,
        *,
        user_query: str,
        additional_hints: list[str] | None = None,
    ) -> AgentResult:
        """
        图节点执行（初始检索）

        参数：
        - user_query: 用户输入
        - additional_hints: 额外的检索提示（如 unknown_tables）

        返回：
        - AgentResult: 执行结果，deliverable 包含分类的知识上下文和可用工具
        """
        if not user_query:
            return AgentResult.failed(
                summary="缺少用户输入",
                error="缺少用户输入",
            )

        search_query = user_query
        if additional_hints:
            hints_str = ", ".join(additional_hints[:20])
            search_query = f"{user_query}\n候选: {hints_str}"

        logger.info(f"🔍 KnowledgeAgent 全局检索: {search_query[:100]}...")

        try:
            ctx = await self.global_search(search_query)
            total_pointers = (
                len(ctx.tables) + len(ctx.columns) + len(ctx.valuedomains) + len(ctx.sqls)
            )

            if total_pointers == 0:
                return AgentResult.completed(
                    summary="知识检索未命中",
                    deliverable={
                        "no_hit": True,
                        "tables": [],
                        "columns": [],
                        "valuedomains": [],
                        "sqls": [],
                        "available_tools": [],
                    },
                    deliverable_type="knowledge",
                )

            available_tools = self.get_available_tools(ctx)

            return AgentResult.completed(
                summary=f"知识检索完成：{total_pointers} 个指针，{len(available_tools)} 个可用工具",
                deliverable={
                    "tables": [t.model_dump() for t in ctx.tables],
                    "columns": [c.model_dump() for c in ctx.columns],
                    "valuedomains": [v.model_dump() for v in ctx.valuedomains],
                    "sqls": [s.model_dump() for s in ctx.sqls],
                    "available_tools": list(available_tools),
                },
                deliverable_type="knowledge",
            )

        except Exception as e:
            logger.error(f"KnowledgeAgent 检索失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"检索失败: {str(e)}",
                error=str(e),
            )

    def _build_knowledge_context(self, context_list: list[dict]) -> KnowledgeContext:
        """
        从原始检索结果构建分类的知识上下文

        参数：
        - context_list: get_nodes_context 返回的节点上下文列表

        返回：
        - KnowledgeContext: 分类的知识上下文
        """
        tables: list[TablePointer] = []
        columns: list[ColumnPointer] = []
        valuedomains: list[ValueDomainPointer] = []
        sqls: list[SqlPointer] = []

        for item in context_list:
            primary_label = item.get("primary_label")
            if not primary_label:
                continue

            score = float(item.get("score") or 0.0)

            if primary_label == "Table":
                tables.append(
                    TablePointer(
                        catalog=item.get("catalog_name") or "",
                        schema_name=item.get("schema_name") or "",
                        table=item.get("name") or "",
                        description=item.get("description"),
                        score=score,
                        tools=["get_table_detail", "get_table_lineage", "get_lineage_sql"],
                    )
                )

            elif primary_label == "Column":
                columns.append(
                    ColumnPointer(
                        catalog=item.get("catalog_name") or "",
                        schema_name=item.get("schema_name") or "",
                        table=item.get("table_name") or "",
                        column=item.get("name") or "",
                        data_type=item.get("data_type"),
                        description=item.get("description"),
                        valuedomain_code=item.get("valuedomain_code"),
                        score=score,
                        tools=["get_table_detail"],
                    )
                )

            elif primary_label == "ValueDomain":
                # 值域直接内联值，无需调用工具
                items_raw = item.get("items") or ""
                values = self._parse_valuedomain_items(items_raw)
                valuedomains.append(
                    ValueDomainPointer(
                        code=item.get("code") or item.get("name") or "",
                        name=item.get("display_name") or item.get("name") or "",
                        domain_type=item.get("domain_type"),
                        values=values,
                        description=item.get("description"),
                        score=score,
                        tools=[],  # 值域自包含，无需工具
                    )
                )

            elif primary_label == "SQL":
                # 解析 SQL 节点的源表和目标表信息
                sqls.append(
                    SqlPointer(
                        sql_id=item.get("node_id") or item.get("code") or "",
                        summary=item.get("description") or item.get("name"),
                        source_tables=item.get("source_tables") or [],
                        target_table=item.get("target_table"),
                        score=score,
                        tools=["get_lineage_sql"],
                    )
                )

        return KnowledgeContext(
            tables=tables,
            columns=columns,
            valuedomains=valuedomains,
            sqls=sqls,
        )

    @staticmethod
    def _parse_valuedomain_items(items_raw: str) -> list[str]:
        """
        解析值域枚举值

        参数：
        - items_raw: JSON 格式的枚举值字符串

        返回：
        - 格式化的枚举值列表，如 ["VALUE=显示名", ...]
        """
        if not items_raw:
            return []

        import json

        try:
            items = json.loads(items_raw)
            if isinstance(items, list):
                result = []
                for item in items:
                    if isinstance(item, dict):
                        code = item.get("code") or item.get("value") or ""
                        name = item.get("name") or item.get("label") or code
                        result.append(f"{code}={name}")
                    else:
                        result.append(str(item))
                return result
        except (json.JSONDecodeError, TypeError):
            pass

        return []
