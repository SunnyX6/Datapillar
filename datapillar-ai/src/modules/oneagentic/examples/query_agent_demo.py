"""
业务 Agent 开发示例

本文件演示如何使用 OneAgentic 框架开发业务 Agent。

框架提供的 API：
- @agent: 装饰器，声明式定义 Agent
- @tool: 装饰器，定义工具（自动注册）
- AgentContext: 执行上下文
- AgentRole: Agent 角色枚举
- Clarification: 澄清请求
- KnowledgeDomain, KnowledgeLevel, KnowledgeStore: 知识系统

业务侧只需要关注：
1. 定义输出 Schema（Pydantic 模型）
2. 使用 @tool 装饰器定义工具（自动注册）
3. 使用 @agent 装饰器声明 Agent
4. 实现 run(self, ctx) 方法
"""

from pydantic import BaseModel, Field

# === 从框架导入（业务侧可用的全部 API）===
from src.modules.oneagentic import (
    AgentContext,
    AgentRole,
    Clarification,
    KnowledgeDomain,
    KnowledgeLevel,
    KnowledgeStore,
    agent,
    tool,
)

# ==================== 1. 定义输出 Schema ====================


class QueryResult(BaseModel):
    """查询结果"""

    answer: str = Field(..., description="回答内容")
    confidence: float = Field(..., ge=0, le=1, description="置信度")
    sources: list[str] = Field(default_factory=list, description="信息来源")
    needs_clarification: bool = Field(False, description="是否需要澄清")
    clarification_questions: list[str] = Field(default_factory=list, description="澄清问题")


# ==================== 2. 定义工具（自动注册）====================


# --- 基础用法：docstring 自动解析 ---
@tool
def search_catalog(keyword: str) -> str:
    """
    搜索数据目录

    Args:
        keyword: 搜索关键词

    Returns:
        匹配的表信息
    """
    return f"找到以下表: users, orders, products (关键词: {keyword})"


# --- 自定义工具名 ---
@tool("get_schema")
def get_table_schema(table_name: str) -> str:
    """
    获取表结构

    Args:
        table_name: 表名

    Returns:
        表的字段信息
    """
    return f"表 {table_name} 的字段: id, name, created_at"


# --- 高级用法：自定义名称 + Pydantic Schema ---
class AdvancedSearchArgs(BaseModel):
    """高级搜索参数"""

    keyword: str = Field(..., description="搜索关键词")
    catalog: str = Field("default", description="数据目录名称")
    limit: int = Field(10, ge=1, le=100, description="返回结果数量")


@tool("advanced_search", args_schema=AdvancedSearchArgs)
def search_with_options(keyword: str, catalog: str = "default", limit: int = 10) -> str:
    """高级搜索：支持指定目录和数量限制"""
    return f"在 {catalog} 中搜索 '{keyword}'，返回前 {limit} 条结果"


# ==================== 3. 定义 Agent ====================


@agent(
    id="query_agent",
    name="查询助手",
    role=AgentRole.EXTERNAL,
    is_entry=True,
    description="回答用户关于数据目录的问题",
    tools=["search_catalog", "get_schema", "advanced_search"],  # 引用工具名
    deliverable_schema=QueryResult,
    deliverable_key="query_result",
    knowledge_domains=["catalog_knowledge"],
    temperature=0.0,
    max_iterations=5,
)
class QueryAgent:
    """查询助手 Agent"""

    SYSTEM_PROMPT = """你是一个数据目录查询助手。

## 你的任务
回答用户关于数据目录、表结构、字段信息的问题。

## 工作流程
1. 分析用户问题
2. 如果需要查询表信息，使用 search_catalog 或 get_table_schema 工具
3. 根据查询结果回答用户问题

## 输出要求
- answer: 清晰的回答
- confidence: 回答的置信度 (0-1)
- sources: 信息来源（表名列表）
- needs_clarification: 如果问题不明确，设为 true
- clarification_questions: 需要用户回答的问题
"""

    async def run(self, ctx: AgentContext) -> QueryResult | Clarification:
        """Agent 执行逻辑"""
        # 1. 构建消息
        messages = ctx.build_messages(self.SYSTEM_PROMPT)

        # 2. 工具调用循环（委派由框架自动处理）
        messages = await ctx.invoke_tools(messages)

        # 3. 获取结构化输出
        output = await ctx.get_output(messages)

        # 4. 业务判断
        if output.needs_clarification and output.clarification_questions:
            return ctx.clarify(
                message="我需要更多信息来回答您的问题",
                questions=output.clarification_questions,
            )

        return output


# ==================== 4. 注册知识 ====================

CATALOG_KNOWLEDGE = KnowledgeDomain(
    domain_id="catalog_knowledge",
    name="数据目录知识",
    level=KnowledgeLevel.DOMAIN,
    content="""## 数据目录知识

### 表命名规范
- 表名使用三段式：catalog.schema.table

### 查询建议
- 先用 search_catalog 模糊搜索
- 再用 get_table_schema 获取具体字段
""",
    tags=["数据目录", "表结构"],
)


def register_knowledge() -> None:
    """注册知识"""
    KnowledgeStore.register_domain(CATALOG_KNOWLEDGE)


# ==================== 5. 初始化函数 ====================


def init_query_agent() -> None:
    """
    初始化 Query Agent

    注意：
    - @tool 装饰的工具已自动注册，无需手动注册
    - @agent 装饰的 Agent 已自动注册，无需手动注册
    - 只需注册知识领域
    """
    register_knowledge()


# ==================== 6. 使用规范总结 ====================

"""
业务 Agent 开发规范：

1. 框架导出的 API（业务侧可用）：
   - agent: Agent 定义装饰器
   - tool: 工具定义装饰器（自动注册）
   - AgentContext: 执行上下文
   - AgentRole: 角色枚举
   - Clarification: 澄清请求
   - KnowledgeDomain, KnowledgeLevel, KnowledgeStore: 知识系统
   - Orchestrator: 编排器（应用层）

2. @tool 使用方式：
   @tool
   def search_tables(keyword: str) -> str:
       '''搜索表'''
       return ...

   # 自动注册，Agent 直接用名称引用：tools=["search_tables"]

3. @agent 使用方式：
   @agent(
       id="query_agent",
       name="查询助手",
       tools=["search_tables"],  # 工具名称列表
       deliverable_schema=QueryResult,
       ...
   )
   class QueryAgent:
       async def run(self, ctx: AgentContext):
           ...

4. run() 方法的标准流程：
   messages = ctx.build_messages(self.SYSTEM_PROMPT)
   messages = await ctx.invoke_tools(messages)  # 委派由框架自动处理
   output = await ctx.get_output(messages)
   # 业务判断...
   return output

5. 业务侧控制点：
   - 系统提示词（SYSTEM_PROMPT）
   - 输出结构（deliverable_schema）
   - 业务判断（如 confidence 检查）
   - 澄清请求（ctx.clarify）

6. 框架自动处理：
   - 工具注册和注入
   - 记忆管理和压缩
   - 知识注入
   - LLM 调用
   - 委派路由
   - 结构化输出
"""
