"""
OneAgentic - 多智能体协作框架

核心 API（推荐）：
- Datapillar: 团队组织，多 Agent 协作入口
- Process: 执行模式（顺序/动态/层级/并行）
- agent: 装饰器，声明式定义 Agent
- tool: 装饰器，注册工具

类型定义：
- AgentContext: 执行上下文
- AgentRole: Agent 角色枚举
- Clarification: 澄清请求

知识系统：
- KnowledgeDomain, KnowledgeLevel, KnowledgeStore

A2A 协议（跨服务通信）：
- A2AConfig, A2AClient, AgentCard

事件系统：
- event_bus, BaseEvent, 各种事件类型

遥测（可观测性）：
- init_telemetry, get_tracer, trace_agent, trace_tool

MCP 协议（工具扩展）：
- MCPClient, filesystem_server, git_server, postgres_server

底层 API（框架内部）：
- Orchestrator: 底层编排器

使用示例：

1. 定义 Agent：
```python
from src.modules.oneagentic import agent, AgentContext, Clarification

@agent(
    id="analyst",
    name="需求分析师",
    tools=["search_tables"],
    can_delegate_to=["architect"],
    deliverable_schema=AnalysisOutput,
    deliverable_key="analysis",
)
class AnalystAgent:
    SYSTEM_PROMPT = "你是需求分析师..."

    async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)  # 委派由框架自动处理
        return await ctx.get_output(messages)
```

2. 组建团队（推荐方式）：
```python
from src.modules.oneagentic import Datapillar, Process

team = Datapillar(
    name="ETL 团队",
    agents=[AnalystAgent, ArchitectAgent, DeveloperAgent],
    process=Process.SEQUENTIAL,
    memory=True,
)

# 流式执行
async for event in team.stream(
    query="帮我创建一个用户宽表",
    session_id="session123",
    user_id="user456",
):
    print(event)

# 或同步执行
result = await team.kickoff(inputs={"query": "创建用户宽表"})
```

3. 事件订阅：
```python
from src.modules.oneagentic import event_bus, AgentStartedEvent

@event_bus.on(AgentStartedEvent)
def on_agent_started(source, event):
    print(f"Agent {event.agent_name} 开始执行")
```

4. A2A 远程调用：
```python
from src.modules.oneagentic import A2AConfig, A2AClient

config = A2AConfig(endpoint="https://partner.com/.well-known/agent-card.json")
async with A2AClient(config) as client:
    result = await client.send_task("分析数据")
```
"""

# === 核心 API（推荐）===

# 团队组织
# === A2A 协议 ===
from src.modules.oneagentic.a2a import (
    A2AClient,
    A2AConfig,
    AgentCard,
    AgentSkill,
    APIKeyAuth,
    BearerAuth,
    create_a2a_tool,
)

# 装饰器
from src.modules.oneagentic.core.agent import agent

# === 类型定义 ===
# 执行上下文
from src.modules.oneagentic.core.context import AgentContext
from src.modules.oneagentic.core.datapillar import Datapillar, DatapillarResult

# 执行模式
from src.modules.oneagentic.core.process import Process

# 类型
from src.modules.oneagentic.core.types import Clarification

# === 事件系统 ===
from src.modules.oneagentic.events import (
    AgentCompletedEvent,
    AgentFailedEvent,
    AgentStartedEvent,
    BaseEvent,
    SessionCompletedEvent,
    SessionStartedEvent,
    ToolCalledEvent,
    ToolCompletedEvent,
    event_bus,
)

# === 知识系统 ===
from src.modules.oneagentic.knowledge import (
    KnowledgeDomain,
    KnowledgeLevel,
    KnowledgeStore,
)

# === MCP 协议 ===
from src.modules.oneagentic.mcp import (
    MCPClient,
    MCPServerHTTP,
    MCPServerSSE,
    MCPServerStdio,
    create_mcp_tools,
    fetch_server,
    filesystem_server,
    git_server,
    memory_server,
    postgres_server,
    sqlite_server,
)

# === 底层 API（框架内部）===
# 编排器
from src.modules.oneagentic.runtime.orchestrator import Orchestrator

# === 遥测 ===
from src.modules.oneagentic.telemetry import (
    get_tracer,
    init_telemetry,
    instrument_events,
    is_telemetry_enabled,
    shutdown_telemetry,
    trace_agent,
    trace_tool,
)

# 工具
from src.modules.oneagentic.tools.registry import tool

__all__ = [
    # === 核心 API ===
    "Datapillar",
    "DatapillarResult",
    "Process",
    "agent",
    "tool",
    # === 类型定义 ===
    "AgentContext",
    "Clarification",
    # === 知识系统 ===
    "KnowledgeDomain",
    "KnowledgeLevel",
    "KnowledgeStore",
    # === A2A 协议 ===
    "A2AConfig",
    "A2AClient",
    "AgentCard",
    "AgentSkill",
    "APIKeyAuth",
    "BearerAuth",
    "create_a2a_tool",
    # === 事件系统 ===
    "event_bus",
    "BaseEvent",
    "AgentStartedEvent",
    "AgentCompletedEvent",
    "AgentFailedEvent",
    "ToolCalledEvent",
    "ToolCompletedEvent",
    "SessionStartedEvent",
    "SessionCompletedEvent",
    # === 遥测 ===
    "init_telemetry",
    "shutdown_telemetry",
    "get_tracer",
    "is_telemetry_enabled",
    "instrument_events",
    "trace_agent",
    "trace_tool",
    # === MCP 协议 ===
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
    # === 底层 API ===
    "Orchestrator",
]
