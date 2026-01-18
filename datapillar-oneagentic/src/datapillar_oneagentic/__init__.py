"""
Datapillar OneAgentic - 声明式多智能体协作框架

快速开始：
```python
from pydantic import BaseModel
from datapillar_oneagentic import agent, tool, Datapillar, Process, DatapillarConfig

# 配置 LLM
config = DatapillarConfig(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"}
)

# 定义工具
@tool
def search_database(query: str) -> str:
    '''搜索数据库'''
    return f"Results for: {query}"

# 定义交付物 Schema
class AnalysisOutput(BaseModel):
    summary: str
    insights: list[str]

# 定义 Agent
@agent(
    id="analyst",
    name="数据分析师",
    deliverable_schema=AnalysisOutput,
    tools=[search_database],
)
class AnalystAgent:
    SYSTEM_PROMPT = "你是数据分析师..."

    async def run(self, ctx):
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        return await ctx.get_structured_output(messages)

# 组建团队并执行（流式）
team = Datapillar(config=config, namespace="my_project", name="分析团队", agents=[AnalystAgent])
async for event in team.stream(query="分析销售数据", session_id="session_001"):
    if event["event"] == "result":
        print(event["result"]["deliverable"])
    elif event["event"] == "agent.interrupt":
        # Agent 需要用户输入，获取用户输入后恢复
        user_input = input(str(event.get("interrupt", {}).get("payload", "")))
        async for e in team.stream(session_id="session_001", resume_value=user_input):
            ...
```

高级功能按需从子模块导入：
- 知识系统: `from datapillar_oneagentic.knowledge import Knowledge, KnowledgeSource, KnowledgeIngestor`
- A2A 协议: `from datapillar_oneagentic.a2a import A2AConfig, create_a2a_tool`
- MCP 协议: `from datapillar_oneagentic.mcp import MCPClient, MCPServerConfig`
- 事件订阅: `team.event_bus`
- 经验学习: `from datapillar_oneagentic.experience import ExperienceLearner`
- SSE 推送: `from datapillar_oneagentic.sse import StreamManager`
"""

__version__ = "0.1.0"

# === 核心 API ===

# 配置
from datapillar_oneagentic.config import DatapillarConfig

# 装饰器
from datapillar_oneagentic.core.agent import agent
from datapillar_oneagentic.core.context import AgentContext

# 核心类
from datapillar_oneagentic.core.datapillar import Datapillar
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.tools.registry import tool

__all__ = [
    # 版本
    "__version__",
    # 配置
    "DatapillarConfig",
    # 装饰器
    "agent",
    "tool",
    # 核心类
    "Datapillar",
    "AgentContext",
    "Process",
    "SessionKey",
]
