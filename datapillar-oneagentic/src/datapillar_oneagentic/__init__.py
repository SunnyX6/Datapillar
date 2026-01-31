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

from __future__ import annotations

from importlib import import_module
from typing import Any

__version__ = "0.1.0"

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

_EXPORTS: dict[str, tuple[str, str]] = {
    "DatapillarConfig": ("datapillar_oneagentic.config", "DatapillarConfig"),
    "agent": ("datapillar_oneagentic.core.agent", "agent"),
    "AgentContext": ("datapillar_oneagentic.core.context", "AgentContext"),
    "Datapillar": ("datapillar_oneagentic.core.datapillar", "Datapillar"),
    "Process": ("datapillar_oneagentic.core.process", "Process"),
    "SessionKey": ("datapillar_oneagentic.core.types", "SessionKey"),
    "tool": ("datapillar_oneagentic.tools.registry", "tool"),
}


def __getattr__(name: str) -> Any:
    target = _EXPORTS.get(name)
    if target is None:
        raise AttributeError(f"module '{__name__}' has no attribute '{name}'")
    module_name, attr_name = target
    module = import_module(module_name)
    value = getattr(module, attr_name)
    globals()[name] = value
    return value


def __dir__() -> list[str]:
    return sorted(set(globals()).union(__all__))
