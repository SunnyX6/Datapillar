# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Datapillar OneAgentic - declarative multi-agent collaboration framework.

Quick start:
```python
from pydantic import BaseModel
from datapillar_oneagentic import agent, tool, Datapillar, Process, DatapillarConfig

# Configure LLM
config = DatapillarConfig(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"}
)

# Define a tool
@tool
def search_database(query: str) -> str:
    '''Search the database.'''
    return f"Results for: {query}"

# Define deliverable schema
class AnalysisOutput(BaseModel):
    summary: str
    insights: list[str]

# Define agent
@agent(
    id="analyst",
    name="Data Analyst",
    deliverable_schema=AnalysisOutput,
    tools=[search_database],
)
    class AnalystAgent:
        SYSTEM_PROMPT = "You are a data analyst."

    async def run(self, ctx):
        messages = ctx.messages().system(self.SYSTEM_PROMPT)
        return await ctx.get_structured_output(messages)

# Build the team and run (streaming)
team = Datapillar(config=config, namespace="my_project", name="Analytics Team", agents=[AnalystAgent])
async for event in team.stream(query="Analyze sales data", session_id="session_001"):
    if event["event"] == "agent.end":
        deliverable = event.get("data", {}).get("deliverable")
        if deliverable is not None:
            print(deliverable)
    elif event["event"] == "agent.interrupt":
        # Agent requires user input; resume after input
        payload = event.get("data", {}).get("interrupt", {}).get("payload", "")
        user_input = input(str(payload))
        async for e in team.stream(session_id="session_001", resume_value=user_input):
            ...
```

Advanced features are available from submodules:
- Knowledge: `from datapillar_oneagentic.knowledge import Knowledge, KnowledgeSource, KnowledgeService`
- A2A: `from datapillar_oneagentic.a2a import A2AConfig, create_a2a_tool`
- MCP: `from datapillar_oneagentic.mcp import MCPClient, MCPServerConfig`
- Event subscription: `team.event_bus`
- Experience learning: `from datapillar_oneagentic.experience import ExperienceLearner`
- SSE streaming: `from datapillar_oneagentic.sse import StreamManager`
"""

__version__ = "0.1.0"

# === Core API ===

# Config
from datapillar_oneagentic.config import DatapillarConfig

# Decorators
from datapillar_oneagentic.core.agent import agent
from datapillar_oneagentic.core.context import AgentContext

# Core classes
from datapillar_oneagentic.core.datapillar import Datapillar
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.tools.registry import tool

__all__ = [
    # Version
    "__version__",
    # Config
    "DatapillarConfig",
    # Decorators
    "agent",
    "tool",
    # Core classes
    "Datapillar",
    "AgentContext",
    "Process",
    "SessionKey",
]
