"""
Knowledge Agent（知识服务）

定位：
- 提供统一的指针查询服务
- 作为图节点处理初始检索和 unknown_tables 委派
- 其他 Agent 按需调用 query_pointers() 获取指针
- 管理 Agent 工具权限

设计原则：
- 指针是"指路"，不是"明细"：不输出列/SQL/全文等大字段
- 严格可验证：资产类指针必须包含 Neo4j element_id
- 按需查询：Agent 需要什么类型的指针就查什么类型
"""

import json
import logging
from typing import Any

from pydantic import BaseModel, Field

from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.tools.agent_tools import search_knowledge_nodes

logger = logging.getLogger(__name__)


# ==================== Agent 类型常量 ====================


class AgentType:
    """Agent 类型"""

    ANALYST = "analyst"
    ARCHITECT = "architect"
    DEVELOPER = "developer"
    TESTER = "tester"


# ==================== Agent 工具权限配置 ====================


AGENT_TOOLS_MAP: dict[str, list[str]] = {
    AgentType.ANALYST: [
        "get_table_columns",
    ],
    AgentType.ARCHITECT: [
        "get_table_lineage",
        "list_component",
    ],
    AgentType.DEVELOPER: [
        "get_table_columns",
        "get_column_valuedomain",
        "get_table_lineage",
        "get_lineage_sql",
    ],
    AgentType.TESTER: [
        "get_table_columns",
        "get_column_valuedomain",
    ],
}


def get_agent_tools(agent_type: str) -> list[str]:
    """获取 Agent 的工具权限列表"""
    return AGENT_TOOLS_MAP.get(agent_type, [])


# ==================== 指针数据结构 ====================


class ETLPointer(BaseModel):
    """
    ETL/资产指针（可指向 Neo4j 中任意 Knowledge 节点）

    约束：
    - 必须可验证：至少包含 element_id
    - 不携带明细：明细通过工具展开
    """

    element_id: str = Field(..., description="Neo4j elementId(node)")
    labels: list[str] = Field(default_factory=list, description="节点 labels")
    primary_label: str | None = Field(default=None, description="主类型")

    node_id: str | None = Field(default=None, description="节点属性 id")
    code: str | None = Field(default=None, description="节点属性 code")

    name: str | None = Field(default=None, description="节点 name")
    display_name: str | None = Field(default=None, description="节点 displayName")
    description: str | None = Field(default=None, description="节点 description")
    tags: list[str] = Field(default_factory=list, description="节点 tags")

    catalog_name: str | None = Field(default=None, description="Catalog 名")
    schema_name: str | None = Field(default=None, description="Schema 名")
    table_name: str | None = Field(default=None, description="Table 名")

    path: str | None = Field(default=None, description="节点路径")
    qualified_name: str | None = Field(default=None, description="规范名")
    score: float | None = Field(default=None, description="检索得分")

    tools: list[str] = Field(default_factory=list, description="可用工具列表")

    model_config = {"extra": "ignore"}


class DocPointer(BaseModel):
    """文档/规范指针（不依赖 Neo4j）"""

    provider: str = Field(..., description="文档提供方")
    ref: dict[str, Any] = Field(default_factory=dict, description="引用信息")

    title: str | None = Field(default=None, description="标题")
    description: str | None = Field(default=None, description="描述")
    tags: list[str] = Field(default_factory=list, description="标签")
    score: float | None = Field(default=None, description="相关性得分")

    tools: list[str] = Field(default_factory=list, description="可用工具列表")

    model_config = {"extra": "ignore"}


class KnowledgeAgent:
    """
    知识检索服务

    方法：
    - run(): 图节点执行（初始检索、unknown_tables 处理）
    - query_pointers(): 按需查询指针（供其他 Agent 调用）
    """

    def __init__(self, *, max_pointers: int = 12, min_score: float = 0.8):
        self.max_pointers = max(1, min(int(max_pointers), 50))
        self.min_score = float(min_score)

    async def run(
        self,
        *,
        user_query: str,
        additional_hints: list[str] | None = None,
    ) -> AgentResult:
        """
        图节点执行（初始检索、unknown_tables 处理）

        参数：
        - user_query: 用户输入
        - additional_hints: 额外的检索提示（如 unknown_tables）

        返回：
        - AgentResult: 执行结果
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

        logger.info(f"🔍 KnowledgeAgent 检索: {search_query[:100]}...")

        try:
            pointers = await self.query_pointers(search_query)
            if not pointers:
                return AgentResult.completed(
                    summary="知识检索未命中",
                    deliverable={"no_hit": True},
                    deliverable_type="knowledge",
                )

            return AgentResult.completed(
                summary=f"知识检索完成：{len(pointers)} 个指针",
                deliverable={
                    "pointers": [p.model_dump() for p in pointers],
                },
                deliverable_type="knowledge",
            )

        except Exception as e:
            logger.error(f"KnowledgeAgent 检索失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"检索失败: {str(e)}",
                error=str(e),
            )

    async def query_pointers(
        self,
        query: str,
        node_types: list[str] | None = None,
        top_k: int | None = None,
        min_score: float | None = None,
    ) -> list[ETLPointer]:
        """
        查询指针（供其他 Agent 按需调用）

        参数：
        - query: 检索查询
        - node_types: 类型过滤（如 ["Table", "Column", "ValueDomain"]）
        - top_k: 召回数量
        - min_score: 最低相关性阈值

        返回：
        - list[ETLPointer]: 指针列表
        """
        actual_top_k = top_k if top_k is not None else self.max_pointers
        actual_min_score = min_score if min_score is not None else self.min_score

        logger.info(
            "🔍 query_pointers(query='%s', node_types=%s, top_k=%s)",
            query[:50],
            node_types,
            actual_top_k,
        )

        raw_json = await search_knowledge_nodes.ainvoke(
            {
                "query": query,
                "top_k": actual_top_k,
                "min_score": actual_min_score,
                "node_types": node_types,
            }
        )

        raw: list[dict] = []
        try:
            parsed = json.loads(raw_json or "")
            if isinstance(parsed, dict) and isinstance(parsed.get("nodes"), list):
                raw = parsed["nodes"]
        except Exception:
            raw = []

        pointers = self._build_pointers(raw)
        logger.info("✅ query_pointers 返回 %d 个指针", len(pointers))
        return pointers

    def _build_pointers(self, raw: list[dict]) -> list[ETLPointer]:
        """从原始检索结果构建 ETLPointer 列表"""
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
        return pointers

    @staticmethod
    def _infer_pointer_tools(labels: list[str] | None) -> list[str]:
        """
        基于节点类型推断可用工具

        知识服务的核心职责：告诉调用方"这个指针能用哪些工具展开"。
        即使某些节点类型暂时没有对应工具，结构也要搭好。
        """
        label_set = set(labels or [])
        tools: list[str] = []

        # Table 节点 - 表级操作
        if "Table" in label_set:
            tools.extend(
                [
                    "get_table_columns",  # 获取表的所有列
                    "get_table_lineage",  # 获取表血缘（含列级映射）
                    "get_lineage_sql",  # 根据血缘查找历史 SQL
                ]
            )

        # Column 节点 - 列级操作
        if "Column" in label_set:
            tools.extend(
                [
                    "get_column_valuedomain",  # 获取列关联的值域
                ]
            )

        # ValueDomain 节点 - 值域本身就是明细，指针已携带 items
        if "ValueDomain" in label_set:
            # 暂无展开工具，指针中的 items 字段已包含枚举值
            pass

        # SQL 节点 - SQL 代码本身就是明细
        if "SQL" in label_set:
            # 暂无展开工具，可通过 get_lineage_sql 按血缘查找
            pass

        # Schema 节点
        if "Schema" in label_set:
            # 暂无展开工具，可通过 search_knowledge_nodes(node_types=["Table"]) 查子表
            pass

        # Catalog 节点
        if "Catalog" in label_set:
            # 暂无展开工具，可通过 get_schema_nav 查看导航
            pass

        # Tag 节点
        if "Tag" in label_set:
            # 暂无展开工具，可通过 get_tag_nav 查看导航
            pass

        # Component 节点
        if "Component" in label_set:
            tools.extend(
                [
                    "list_component",  # 列出组件列表
                ]
            )

        return tools
