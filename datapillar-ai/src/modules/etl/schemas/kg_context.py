"""
知识图谱上下文数据结构（分层设计）

设计原则：
1. 全局上下文只做导航，不存储细节
2. 细节通过 Tool 按需查询
3. 上下文分层：GlobalKGContext（全局导航）→ AgentScopedContext（Agent专属指针+工具）
4. 工具也是上下文的一部分，告诉 Agent 可以用哪些工具获取详情

参考：Datus-agent 的 ScopedContext 设计
"""

from pydantic import BaseModel, Field


# ==================== 轻量导航模型 ====================


class TableNav(BaseModel):
    """表导航信息（轻量）"""

    name: str = Field(..., description="表名")
    schema_name: str = Field(..., description="Schema 名")
    catalog: str = Field(..., description="Catalog 名")
    tags: list[str] = Field(default_factory=list, description="标签（分层/业务域）")
    description: str | None = Field(default=None, description="表描述")
    column_count: int = Field(default=0, description="列数量")


class SchemaNav(BaseModel):
    """Schema 导航信息（轻量）"""

    name: str = Field(..., description="Schema 名")
    catalog: str = Field(..., description="Catalog 名")
    description: str | None = Field(default=None, description="Schema 描述")
    tables: list[TableNav] = Field(default_factory=list, description="表列表")


class CatalogNav(BaseModel):
    """Catalog 导航信息（轻量）"""

    name: str = Field(..., description="Catalog 名")
    metalake: str = Field(..., description="Metalake 名")
    schemas: list[SchemaNav] = Field(default_factory=list, description="Schema 列表")


class LineageEdge(BaseModel):
    """表级血缘边（轻量）"""

    source_table: str = Field(..., description="源表（schema.table）")
    target_table: str = Field(..., description="目标表（schema.table）")
    sql_id: str | None = Field(default=None, description="关联的 SQL 节点 ID")


class ComponentNav(BaseModel):
    """组件导航信息"""

    id: int | None = Field(default=None, description="组件 ID（对应 job_component.id）")
    code: str = Field(..., description="组件代码（HIVE/SPARK_SQL/SHELL）")
    name: str = Field(..., description="组件名称")
    type: str = Field(..., description="组件类型（SQL/SCRIPT/SYNC）")


# ==================== 全局知识图谱上下文 ====================


class GlobalKGContext(BaseModel):
    """
    全局知识图谱上下文（轻量导航）

    所有 Agent 共享的全局知识导航，只存储导航信息，不存储细节。
    细节通过 Tool 按需查询。
    """

    # 数据资产导航（层级结构）
    catalogs: list[CatalogNav] = Field(default_factory=list, description="Catalog 列表")

    # 表级血缘图（轻量边）
    lineage_graph: list[LineageEdge] = Field(default_factory=list, description="血缘关系")

    # 可用组件
    components: list[ComponentNav] = Field(default_factory=list, description="组件列表")

    # ==================== 导航方法 ====================

    def get_all_tables(self) -> list[TableNav]:
        """获取所有表"""
        tables = []
        for catalog in self.catalogs:
            for schema in catalog.schemas:
                tables.extend(schema.tables)
        return tables

    def get_tables_by_tag(self, tag: str) -> list[TableNav]:
        """按标签筛选表"""
        return [t for t in self.get_all_tables() if tag in t.tags]

    def get_tables_by_layer(self, layer: str) -> list[TableNav]:
        """按分层筛选表（layer:ODS, layer:DWD 等）"""
        tag = f"layer:{layer}"
        return self.get_tables_by_tag(tag)

    def get_table_names(self) -> list[str]:
        """获取所有表名（schema.table 格式）"""
        return [f"{t.schema_name}.{t.name}" for t in self.get_all_tables()]

    def get_upstream_tables(self, table_name: str) -> list[str]:
        """获取上游表"""
        return [e.source_table for e in self.lineage_graph if e.target_table == table_name]

    def get_downstream_tables(self, table_name: str) -> list[str]:
        """获取下游表"""
        return [e.target_table for e in self.lineage_graph if e.source_table == table_name]

    def get_component_codes(self) -> list[str]:
        """获取所有组件代码"""
        return [c.code for c in self.components]

    def find_table(self, table_name: str) -> TableNav | None:
        """查找表（支持 schema.table 或 table 格式）"""
        for t in self.get_all_tables():
            if f"{t.schema_name}.{t.name}" == table_name or t.name == table_name:
                return t
        return None


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
        "search_assets",        # 语义搜索数据资产
        "get_table_columns",    # 获取表列详情（验证表是否存在）
    ],
    AgentType.ARCHITECT: [
        "get_table_lineage",    # 获取表级血缘（设计数据流）
        "list_component",       # 获取可用组件
    ],
    AgentType.DEVELOPER: [
        "get_table_columns",    # 获取表列详情（生成 SQL）
        "get_column_lineage",   # 获取列级血缘（字段映射）
        "get_sql_by_lineage",   # 根据血缘精准匹配历史 SQL（包含 JOIN 条件！）
    ],
    AgentType.TESTER: [
        "get_table_columns",    # 获取表列详情（验证字段）
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

    # 可用的工具列表（告诉 Agent 可以调用哪些工具获取详情）
    tools: list[str] = Field(default_factory=list, description="可用的工具名列表")

    # 用户原始需求（供 Agent 参考）
    user_query: str = Field(default="", description="用户原始需求")

    @classmethod
    def create_for_agent(
        cls,
        agent_type: str,
        tables: list[str],
        user_query: str = "",
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
            tools=tools,
            user_query=user_query,
        )

    def get_tools_description(self) -> str:
        """获取工具描述（供 prompt 使用）"""
        tool_descriptions = {
            "search_assets": "语义搜索数据资产，查找相关表",
            "get_table_columns": "获取表的列详情（列名、类型、描述）",
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

