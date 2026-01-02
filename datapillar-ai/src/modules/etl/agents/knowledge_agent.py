"""
Knowledge Agent（知识服务：指针编译器）

定位：
- 这是“知识服务层”，不是“流程控制/人机交互”Agent
- 只负责把输入线索编译为可验证的指针集合（以及按 Agent 的最小权限下发可用工具）

设计原则：
- 指针是“指路”，不是“明细”：不输出列/SQL/全文等大字段
- 严格可验证：资产类指针必须包含 Neo4j element_id
- Pull-first：是否触发检索由上游 Agent/黑板路由决定；本服务不抢占用户交互
"""

import json
import logging

from langchain_core.messages import AIMessage
from langgraph.types import Command

from src.modules.etl.schemas.kg_context import AgentScopedContext, AgentType, ETLPointer
from src.modules.etl.schemas.requests import BlackboardRequest
from src.modules.etl.schemas.state import AgentState
from src.modules.etl.tools.agent_tools import search_knowledge_nodes

logger = logging.getLogger(__name__)

class KnowledgeAgent:
    """
    知识检索专家

    职责：
    1. 产出可验证的 ETLPointer（指向任意 Knowledge 节点）
    2. 生成严格的 schema.table 列表（仅当命中表节点时）
    3. 为指针注入可展开工具名（ETLPointer.tools）
    4. 下发 AgentScopedContext（指针 + 工具 allowlist）
    """

    def __init__(self, *, max_pointers: int = 12, min_score: float = 0.8):
        self.max_pointers = max(1, min(int(max_pointers), 50))
        self.min_score = float(min_score)

    async def __call__(self, state: AgentState) -> Command:
        """执行知识检索"""
        user_query = state.user_input
        # Pull-first：当上游通过 delegate 触发刷新时，尽量利用 payload 中的线索提升召回
        if state.pending_requests:
            req0_raw = state.pending_requests[0]
            req0 = BlackboardRequest(**req0_raw) if isinstance(req0_raw, dict) else req0_raw
            if req0.kind == "delegate" and (req0.target_agent in {"knowledge_agent", "知识检索专家"}):
                payload = dict(req0.payload or {})
                if payload.get("type") == "refresh_knowledge":
                    unknown_tables = payload.get("unknown_tables") or []
                    if isinstance(unknown_tables, list):
                        hints = [t.strip() for t in unknown_tables if isinstance(t, str) and t.strip()]
                        if hints:
                            user_query = f"{user_query}\n候选表: {', '.join(hints[:20])}"
        if not user_query:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少用户输入，无法检索知识")],
                    "current_agent": "knowledge_agent",
                    "error": "缺少用户输入",
                }
            )

        try:
            etl_pointers = await self._retrieve_node_pointers(user_query)
            if not etl_pointers:
                return Command(
                    update={
                        "messages": [AIMessage(content="知识检索未命中：未找到可验证的知识指针")],
                        "current_agent": "knowledge_agent",
                        "metadata": {
                            **state.metadata,
                            "knowledge_user_input": user_query,
                            "knowledge_no_hit": {"user_query": user_query},
                        },
                    }
                )
            qualified_tables = self._build_strict_schema_table_list(etl_pointers)

            agent_contexts = self._create_agent_contexts(
                qualified_tables=qualified_tables,
                etl_pointers=etl_pointers,
            )

            summary = f"知识检索完成：命中 {len(etl_pointers)} 个指针"
            ai_message = AIMessage(content=summary)

            return Command(
                update={
                    "messages": [
                        ai_message
                    ],
                    "agent_contexts": {k: v.model_dump() for k, v in agent_contexts.items()},
                    "current_agent": "knowledge_agent",
                    "metadata": {
                        **state.metadata,
                        "knowledge_user_input": user_query,
                        "knowledge_agent": {
                            "summary": summary,
                            "etl_pointers": [p.model_dump() for p in etl_pointers],
                        },
                    },
                }
            )

        except Exception as e:
            logger.error(f"KnowledgeAgent 检索失败: {e}", exc_info=True)
            return Command(
                update={
                    "messages": [AIMessage(content=f"知识检索失败: {str(e)}")],
                    "current_agent": "knowledge_agent",
                    "error": str(e),
                }
            )

    async def _retrieve_node_pointers(self, user_query: str) -> list[ETLPointer]:
        """
        产出通用节点指针列表（严格可验证）

        策略：
        1) 语义检索（向量/全文/混合）召回候选 Knowledge 节点 element_id
        2) 精确取回上下文并组装为可验证 ETLPointer

        约束：
        - 不对用户自由文本做“显式 schema.table 解析”短路；这属于输入规范问题，不应由后端猜测前端写法。
        """
        raw_json = await search_knowledge_nodes.ainvoke(
            {"query": user_query, "top_k": self.max_pointers, "min_score": self.min_score}
        )
        raw: list[dict] = []
        try:
            parsed = json.loads(raw_json or "")
            if isinstance(parsed, dict) and isinstance(parsed.get("nodes"), list):
                raw = parsed["nodes"]
        except Exception:
            raw = []

        pointers: list[ETLPointer] = []
        for item in raw:
            element_id = item.get("element_id")
            if not element_id:
                continue
            labels = item.get("labels") or []
            schema_name = item.get("schema_name")
            name = item.get("name")
            table_name = item.get("table_name")
            if not table_name and "Table" in set(labels or []) and name:
                table_name = name

            qualified_name = item.get("qualified_name")
            if not qualified_name:
                if "Table" in set(labels or []) and schema_name and table_name:
                    qualified_name = f"{schema_name}.{table_name}"
                elif "Column" in set(labels or []) and schema_name and table_name and name:
                    qualified_name = f"{schema_name}.{table_name}.{name}"

            pointers.append(
                ETLPointer(
                    element_id=element_id,
                    labels=labels,
                    primary_label=item.get("primary_label"),
                    node_id=item.get("node_id"),
                    code=item.get("code"),
                    name=name,
                    display_name=item.get("display_name"),
                    description=item.get("description"),
                    tags=item.get("tags") or [],
                    catalog_name=item.get("catalog_name"),
                    schema_name=schema_name,
                    table_name=table_name,
                    path=item.get("path"),
                    qualified_name=qualified_name,
                    score=float(item.get("score") or 0.0),
                    tools=self._infer_pointer_tools(labels),
                )
            )

        if not pointers:
            return []
        return pointers

    @staticmethod
    def _infer_pointer_tools(labels: list[str] | None) -> list[str]:
        """
        基于节点类型给出“可展开工具”建议。

        说明：
        - 这里只返回工具名，不塞任何工具说明/提示词
        - 最终下发时会按 Agent allowlist 过滤
        """
        label_set = set(labels or [])
        if "Table" in label_set:
            return [
                "get_table_columns",
                "get_table_lineage",
                "get_column_lineage",
                "get_sql_by_lineage",
            ]
        if "Column" in label_set:
            return ["get_column_value_domain"]
        return []

    def _build_strict_schema_table_list(self, node_pointers: list[ETLPointer]) -> list[str]:
        """
        严格：只输出 schema.table（保持“检索顺序”，避免无意义的字母序扰动）
        """
        schema_tables: list[str] = []
        seen: set[str] = set()
        for p in node_pointers:
            if "Table" not in set(p.labels or []):
                continue
            if not p.schema_name or not (p.table_name or p.name):
                continue
            qualified = f"{p.schema_name}.{p.table_name or p.name}"
            if qualified in seen:
                continue
            seen.add(qualified)
            schema_tables.append(qualified)
        return schema_tables

    def _create_agent_contexts(
        self,
        qualified_tables: list[str],
        etl_pointers: list[ETLPointer],
    ) -> dict[str, AgentScopedContext]:
        """
        为每个 Agent 创建专属上下文

        每个 Agent 的上下文包含：
        - 可访问的表名列表（指针）
        - 通用节点指针（可指向任意 Knowledge 节点）
        - 可用的工具列表

        详情由各 Agent 自己通过工具获取。
        """
        contexts = {}

        contexts[AgentType.ANALYST] = AgentScopedContext.create_for_agent(
            agent_type=AgentType.ANALYST,
            tables=qualified_tables,
            etl_pointers=self._filter_pointer_tools_by_allowlist(etl_pointers, AgentType.ANALYST),
        )

        contexts[AgentType.ARCHITECT] = AgentScopedContext.create_for_agent(
            agent_type=AgentType.ARCHITECT,
            tables=qualified_tables,
            etl_pointers=self._filter_pointer_tools_by_allowlist(etl_pointers, AgentType.ARCHITECT),
        )

        contexts[AgentType.DEVELOPER] = AgentScopedContext.create_for_agent(
            agent_type=AgentType.DEVELOPER,
            tables=qualified_tables,
            etl_pointers=self._filter_pointer_tools_by_allowlist(etl_pointers, AgentType.DEVELOPER),
        )

        contexts[AgentType.TESTER] = AgentScopedContext.create_for_agent(
            agent_type=AgentType.TESTER,
            tables=qualified_tables,
            etl_pointers=self._filter_pointer_tools_by_allowlist(etl_pointers, AgentType.TESTER),
        )

        return contexts

    @staticmethod
    def _filter_pointer_tools_by_allowlist(node_pointers: list[ETLPointer], agent_type: str) -> list[ETLPointer]:
        allowlist = set(AgentScopedContext.create_for_agent(agent_type=agent_type, tables=[]).tools)
        filtered: list[ETLPointer] = []
        for p in node_pointers:
            tools = [t for t in (p.tools or []) if t in allowlist]
            filtered.append(p.model_copy(update={"tools": tools}))
        return filtered
