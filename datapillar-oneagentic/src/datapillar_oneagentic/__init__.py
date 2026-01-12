"""
Datapillar OneAgentic - 声明式多智能体协作框架

快速开始：
```python
from datapillar_oneagentic import agent, tool, Datapillar, Process, datapillar_configure

# 必须先配置 LLM（框架不提供默认值）
datapillar_configure(
    api_key="sk-xxx",
    model="gpt-4o",
)

# 定义工具
@tool
def search_database(query: str) -> str:
    '''搜索数据库'''
    return f"Results for: {query}"

# 定义 Agent
@agent(
    id="analyst",
    name="数据分析师",
    tools=["search_database"],
    deliverable_schema=AnalysisOutput,
)
class AnalystAgent:
    SYSTEM_PROMPT = "你是数据分析师..."

    async def run(self, ctx):
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_output(messages)

# 组建团队
team = Datapillar(
    name="分析团队",
    agents=[AnalystAgent],
    process=Process.SEQUENTIAL,
)

# 执行
result = await team.kickoff(inputs={"query": "分析销售数据"})
```

知识系统：
```python
from datapillar_oneagentic.knowledge import KnowledgeDomain, KnowledgeLevel, KnowledgeStore
```

A2A 远程调用：
```python
from datapillar_oneagentic.a2a import A2AConfig, A2AClient, AgentCard
```

经验学习（需要配置 Embedding）：
```python
from datapillar_oneagentic import datapillar_configure
from datapillar_oneagentic.experience import ExperienceLearner, Episode
from datapillar_oneagentic.storage.learning_stores import LanceVectorStore

# 使用经验学习需要配置 Embedding
datapillar_configure(
    api_key="sk-xxx",
    model="gpt-4o",
    embedding_api_key="sk-xxx",
    embedding_model="text-embedding-3-small",
)
```

ReAct 规划：
```python
from datapillar_oneagentic.react import Plan, create_plan, reflect
```
"""

__version__ = "0.1.0"

# === 核心 API ===

# 配置
from datapillar_oneagentic.config import (
    datapillar_configure,
    get_config,
    ConfigurationError,
    DatapillarConfig,
)

# 装饰器
from datapillar_oneagentic.core.agent import agent
from datapillar_oneagentic.tools.registry import tool

# 核心类
from datapillar_oneagentic.core.datapillar import Datapillar, DatapillarResult
from datapillar_oneagentic.core.context import AgentContext
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.types import Clarification

# 事件系统
from datapillar_oneagentic.events import (
    event_bus,
    EventBus,
    BaseEvent,
    AgentStartedEvent,
    AgentCompletedEvent,
    AgentFailedEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    SessionStartedEvent,
    SessionCompletedEvent,
)

# === 知识系统 ===

from datapillar_oneagentic.knowledge import (
    KnowledgeDomain,
    KnowledgeLevel,
    KnowledgeStore,
)

# === A2A 协议 ===

from datapillar_oneagentic.a2a import (
    A2AConfig,
    A2AClient,
    AgentCard,
    AgentSkill,
    APIKeyAuth,
    BearerAuth,
    create_a2a_tool,
)

# === MCP 协议 ===

from datapillar_oneagentic.mcp import (
    MCPClient,
    MCPServerStdio,
    MCPServerHTTP,
    MCPServerSSE,
    create_mcp_tools,
    filesystem_server,
    git_server,
    memory_server,
    postgres_server,
    sqlite_server,
    fetch_server,
)

# === 遥测（可观测性）===

from datapillar_oneagentic.telemetry import (
    init_telemetry,
    shutdown_telemetry,
    get_tracer,
    is_telemetry_enabled,
    instrument_events,
    trace_agent,
    trace_tool,
)

# === SSE 流式推送 ===

from datapillar_oneagentic.sse import (
    SseEvent,
    SseEventType,
    StreamManager,
)

# === 经验学习 ===

from datapillar_oneagentic.experience import (
    Episode,
    EpisodeStep,
    Outcome,
    ExperienceRetriever,
    ExperienceLearner,
    SedimentationPolicy,
    DefaultSedimentationPolicy,
)

# === ReAct 规划 ===

from datapillar_oneagentic.react import (
    Plan,
    PlanTask,
    Reflection,
    create_plan,
    replan,
    reflect,
    decide_next_action,
)

# === Token 计算与 Usage 追踪 ===

# Token 计数器
from datapillar_oneagentic.providers.token_counter import (
    TiktokenCounter,
    get_token_counter,
)

# Token 估算（快捷函数）
from datapillar_oneagentic.providers.llm.token_counter import (
    estimate_text_tokens,
    estimate_messages_tokens,
)

# Usage 追踪（兼容多模型：OpenAI/Anthropic/GLM 等）
from datapillar_oneagentic.providers.llm.usage_tracker import (
    NormalizedTokenUsage,
    ModelPricingUsd,
    UsageCostUsd,
    extract_usage,
    estimate_usage,
    estimate_cost_usd,
    parse_pricing,
)

# === 扩展 API（高级用户，用于自定义提供者和存储后端）===

# Token 计数器基类（用于自定义实现）
from datapillar_oneagentic.providers.token_counter import BaseTokenCounter

# 存储后端（业务侧选择存储实现）
from datapillar_oneagentic.storage import (
    # Checkpointers
    MemoryCheckpointer,
    SqliteCheckpointer,
    PostgresCheckpointer,
    RedisCheckpointer,
    # Deliverable Stores
    InMemoryDeliverableStore,
    PostgresDeliverableStore,
    RedisDeliverableStore,
    # Learning Stores
    VectorStore,
    LanceVectorStore,
)

__all__ = [
    # 版本
    "__version__",
    # 配置
    "datapillar_configure",
    "get_config",
    "ConfigurationError",
    "DatapillarConfig",
    # 装饰器
    "agent",
    "tool",
    # 核心类
    "Datapillar",
    "DatapillarResult",
    "AgentContext",
    "Process",
    "Clarification",
    # 事件
    "event_bus",
    "EventBus",
    "BaseEvent",
    "AgentStartedEvent",
    "AgentCompletedEvent",
    "AgentFailedEvent",
    "ToolCalledEvent",
    "ToolCompletedEvent",
    "SessionStartedEvent",
    "SessionCompletedEvent",
    # 知识系统
    "KnowledgeDomain",
    "KnowledgeLevel",
    "KnowledgeStore",
    # A2A 协议
    "A2AConfig",
    "A2AClient",
    "AgentCard",
    "AgentSkill",
    "APIKeyAuth",
    "BearerAuth",
    "create_a2a_tool",
    # MCP 协议
    "MCPClient",
    "MCPServerStdio",
    "MCPServerHTTP",
    "MCPServerSSE",
    "create_mcp_tools",
    "filesystem_server",
    "git_server",
    "memory_server",
    "postgres_server",
    "sqlite_server",
    "fetch_server",
    # 遥测
    "init_telemetry",
    "shutdown_telemetry",
    "get_tracer",
    "is_telemetry_enabled",
    "instrument_events",
    "trace_agent",
    "trace_tool",
    # SSE
    "SseEvent",
    "SseEventType",
    "StreamManager",
    # 经验学习
    "Episode",
    "EpisodeStep",
    "Outcome",
    "ExperienceRetriever",
    "ExperienceLearner",
    "SedimentationPolicy",
    "DefaultSedimentationPolicy",
    # ReAct
    "Plan",
    "PlanTask",
    "Reflection",
    "create_plan",
    "replan",
    "reflect",
    "decide_next_action",
    # Token 计算
    "TiktokenCounter",
    "get_token_counter",
    "estimate_text_tokens",
    "estimate_messages_tokens",
    # Usage 追踪
    "NormalizedTokenUsage",
    "ModelPricingUsd",
    "UsageCostUsd",
    "extract_usage",
    "estimate_usage",
    "estimate_cost_usd",
    "parse_pricing",
    # 扩展 API
    "BaseTokenCounter",
    # 存储后端
    "MemoryCheckpointer",
    "SqliteCheckpointer",
    "PostgresCheckpointer",
    "RedisCheckpointer",
    "InMemoryDeliverableStore",
    "PostgresDeliverableStore",
    "RedisDeliverableStore",
    "VectorStore",
    "LanceVectorStore",
]
