"""
Datapillar OneAgentic - 声明式多智能体协作框架

快速开始：
```python
from datapillar_oneagentic import agent, tool, Datapillar, Process, datapillar_configure

# 配置 LLM
datapillar_configure(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"}
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
)
class AnalystAgent:
    SYSTEM_PROMPT = "你是数据分析师..."

    async def run(self, ctx):
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        return await ctx.get_output(messages)

# 组建团队并执行
team = Datapillar(name="分析团队", agents=[AnalystAgent])
result = await team.kickoff(inputs={"query": "分析销售数据"})
```

高级功能按需从子模块导入：
- 知识系统: `from datapillar_oneagentic.context.knowledge import KnowledgeRegistry`
- A2A 协议: `from datapillar_oneagentic.a2a import A2AConfig, create_a2a_tool`
- MCP 协议: `from datapillar_oneagentic.mcp import MCPClient, MCPServerConfig`
- 事件订阅: `from datapillar_oneagentic.events import event_bus`
- 经验学习: `from datapillar_oneagentic.experience import ExperienceLearner`
- SSE 推送: `from datapillar_oneagentic.sse import StreamManager`
- 遥测监控: `from datapillar_oneagentic.telemetry import init_telemetry`
"""

__version__ = "0.1.0"

# === 核心 API ===

# 配置
from datapillar_oneagentic.config import datapillar_configure

# 装饰器
from datapillar_oneagentic.core.agent import agent
from datapillar_oneagentic.tools.registry import tool

# 核心类
from datapillar_oneagentic.core.datapillar import Datapillar
from datapillar_oneagentic.core.result import DatapillarResult
from datapillar_oneagentic.core.context import AgentContext
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.types import Clarification

__all__ = [
    # 版本
    "__version__",
    # 配置
    "datapillar_configure",
    # 装饰器
    "agent",
    "tool",
    # 核心类
    "Datapillar",
    "DatapillarResult",
    "AgentContext",
    "Process",
    "Clarification",
]
