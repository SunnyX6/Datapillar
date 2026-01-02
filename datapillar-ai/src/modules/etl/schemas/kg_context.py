"""
知识图谱上下文数据结构（分层设计）

设计原则：
1. 只存储“指针”与“权限”（AgentScopedContext），不存储任何全局导航大对象
2. 细节与证据必须通过 Tool 按需查询
3. ETL 指针（ETLPointer）是资产类知识入口：必须可验证 element_id（Neo4j）
4. 工具是能力与权限：AgentScopedContext.tools 控制 allowlist

参考：Datus-agent 的 ScopedContext 设计
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


# ==================== Agent 专属上下文 ====================


# Agent 类型常量
class AgentType:
    """Agent 类型"""
    ANALYST = "analyst"
    ARCHITECT = "architect"
    DEVELOPER = "developer"
    TESTER = "tester"


# Agent 可用工具映射
AGENT_TOOLS_MAP: dict[str, list[str]] = {
    AgentType.ANALYST: [
        "get_table_columns",    # 获取表列详情（验证表是否存在）
    ],
    AgentType.ARCHITECT: [
        "get_table_lineage",    # 获取表级血缘（设计数据流）
        "list_component",       # 获取可用组件
    ],
    AgentType.DEVELOPER: [
        "get_table_columns",    # 获取表列详情（生成 SQL）
        "get_column_value_domain",  # 获取列关联值域（枚举映射）
        "get_column_lineage",   # 获取列级血缘（字段映射）
        "get_sql_by_lineage",   # 根据血缘精准匹配历史 SQL（包含 JOIN 条件！）
    ],
    AgentType.TESTER: [
        "get_table_columns",    # 获取表列详情（验证字段）
        "get_column_value_domain",  # 获取列关联值域（枚举映射）
    ],
}


class AgentScopedContext(BaseModel):
    """
    Agent 专属知识上下文

    设计原则（参考 Datus-agent）：
    1. 只存储指针（表名、SQL ID），不存储详情
    2. 详情通过工具按需获取
    3. 工具也是上下文的一部分，告诉 Agent 可以用哪些工具获取详情

    用法：
    - KnowledgeAgent 根据用户需求和 Agent 职责，为每个 Agent 准备 AgentScopedContext
    - Agent 使用 tables 作为导航指针，通过 tools 获取详情
    """

    # Agent 类型
    agent_type: str = Field(..., description="Agent 类型（analyst/architect/developer/tester）")

    # 可访问的表名列表（指针，不是详情）
    tables: list[str] = Field(default_factory=list, description="可访问的表名列表")

    # ETL 指针（可指向 Neo4j 中任意资产节点）
    etl_pointers: list["ETLPointer"] = Field(
        default_factory=list,
        description="ETL/资产指针列表（可指向 Neo4j 节点；不包含明细）",
    )

    # 文档/规范指针（不依赖 Neo4j，可扩展到任意外部知识源）
    doc_pointers: list["DocPointer"] = Field(
        default_factory=list,
        description="文档/规范指针列表（不包含明细；需通过工具按需解析为证据）",
    )

    # 可用的工具列表（告诉 Agent 可以调用哪些工具获取详情）
    tools: list[str] = Field(default_factory=list, description="可用的工具名列表")

    model_config = {"extra": "ignore"}

    @classmethod
    def create_for_agent(
        cls,
        agent_type: str,
        tables: list[str],
        etl_pointers: list["ETLPointer"] | None = None,
        doc_pointers: list["DocPointer"] | None = None,
    ) -> "AgentScopedContext":
        """
        为指定 Agent 创建上下文

        Args:
            agent_type: Agent 类型
            tables: 可访问的表名列表
            user_query: 用户原始需求

        Returns:
            AgentScopedContext 实例
        """
        tools = AGENT_TOOLS_MAP.get(agent_type, [])
        return cls(
            agent_type=agent_type,
            tables=tables,
            etl_pointers=etl_pointers or [],
            doc_pointers=doc_pointers or [],
            tools=tools,
        )

    def get_tools_description(self) -> str:
        """获取工具描述（供 prompt 使用）"""
        tool_descriptions = {
            "search_assets": "统一检索 Knowledge 节点（表/列/值域/指标等），并返回可验证 element_id",
            "resolve_doc_pointer": "解析文档指针为可引用证据（返回内容片段与来源信息）",
            "get_table_columns": "获取表的列详情（列名、类型、描述）",
            "get_column_value_domain": "获取列关联的值域（枚举/范围等），用于将自然语言取值映射到真实取值",
            "get_table_lineage": "获取表的上下游血缘关系",
            "get_column_lineage": "获取列级血缘映射（字段对应关系）",
            "get_sql_by_lineage": "根据血缘精准匹配历史 SQL（包含完整 JOIN 条件，直接参考！）",
            "list_component": "获取可用的大数据组件列表",
        }
        lines = []
        for tool_name in self.tools:
            desc = tool_descriptions.get(tool_name, "")
            lines.append(f"- {tool_name}: {desc}")
        return "\n".join(lines)


class ETLPointer(BaseModel):
    """
    ETL/资产指针（可指向 Neo4j 中任意 Knowledge 节点）

    约束：
    - 必须可“再解析验证”：至少包含 element_id
    - 不携带明细：明细需要通过工具/查询展开
    """

    element_id: str = Field(..., description="Neo4j elementId(node)")
    labels: list[str] = Field(default_factory=list, description="节点 labels 列表")
    primary_label: str | None = Field(default=None, description="主类型（优先 Knowledge 子类 label）")

    node_id: str | None = Field(default=None, description="节点属性 id（如果存在）")
    code: str | None = Field(default=None, description="节点属性 code（如果存在，如指标/语义资产）")

    name: str | None = Field(default=None, description="节点 name")
    display_name: str | None = Field(default=None, description="节点 displayName（可选）")
    description: str | None = Field(default=None, description="节点 description（可选）")
    tags: list[str] = Field(default_factory=list, description="节点 tags（可选）")

    catalog_name: str | None = Field(default=None, description="Catalog 名（可选）")
    schema_name: str | None = Field(default=None, description="Schema 名（可选）")
    table_name: str | None = Field(default=None, description="Table 名（可选）")

    path: str | None = Field(default=None, description="节点在知识图谱中的路径（可选）")
    qualified_name: str | None = Field(default=None, description="对 Agent 友好的规范名（可选）")
    score: float | None = Field(default=None, description="检索相关性得分（可选）")

    tools: list[str] = Field(default_factory=list, description="该节点可用的工具名列表（按 Agent allowlist 过滤后下发）")

    model_config = {"extra": "ignore"}


class DocPointer(BaseModel):
    """
    文档/规范指针（不依赖 Neo4j）

    目标：
    - 可定位：provider + ref 定义“指向哪里”
    - 可验证：必须能通过工具 resolve_doc_pointer 解析为可引用证据
    - 可授权：tools 声明允许对该指针执行哪些工具展开

    约束：
    - 不携带明细：不直接塞全文/大段内容；证据由工具按需返回
    - ref 结构不做强约束：由 provider 自行定义（例如 url/path/doc_id/chunk_id 等）
    """

    provider: str = Field(..., description="文档指针提供方（例如 url/gitlab/vectordb 等；不做枚举死）")
    ref: dict[str, Any] = Field(default_factory=dict, description="不透明引用（由 provider 自行定义）")

    title: str | None = Field(default=None, description="标题（可选）")
    description: str | None = Field(default=None, description="描述（可选）")
    tags: list[str] = Field(default_factory=list, description="标签（可选）")
    score: float | None = Field(default=None, description="相关性得分（可选）")

    tools: list[str] = Field(
        default_factory=list,
        description="该文档指针可用的工具名列表（按 Agent allowlist 过滤后下发）",
    )

    model_config = {"extra": "ignore"}
