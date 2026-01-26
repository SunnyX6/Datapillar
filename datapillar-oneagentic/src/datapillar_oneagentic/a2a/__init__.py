"""
A2A (Agent-to-Agent) protocol module.

Built on Google's official a2a-sdk for cross-service remote agent calls.

Core components:
- A2AConfig: remote agent connection config
- create_a2a_tool: create an A2A delegation tool
- create_a2a_tools: batch create A2A tools

Example (agent level):
```python
from datapillar_oneagentic import agent
from datapillar_oneagentic.a2a import A2AConfig, APIKeyAuth

@agent(
    id="analyst",
    name="Analyst",
    deliverable_schema=AnalysisOutput,
    a2a_agents=[
        A2AConfig(
            endpoint="https://remote-agent.example.com/.well-known/agent.json",
            auth=APIKeyAuth(api_key="sk-xxx"),
        ),
    ],
)
class AnalystAgent:
    ...
```

Example (team level):
```python
from datapillar_oneagentic import Datapillar
from datapillar_oneagentic.a2a import A2AConfig

dp = Datapillar(
    agents=["analyst", "coder"],
    a2a_agents=[
        A2AConfig(endpoint="https://shared-agent.example.com/.well-known/agent.json"),
    ],
)
```

Install A2A support:
```bash
pip install datapillar-oneagentic[a2a]
```
"""

from datapillar_oneagentic.a2a.config import (
    A2AConfig,
    APIKeyAuth,
    AuthScheme,
    AuthType,
    BearerAuth,
)
from datapillar_oneagentic.a2a.tool import (
    create_a2a_tool,
    create_a2a_tools,
)

__all__ = [
    # Config
    "A2AConfig",
    "AuthScheme",
    "APIKeyAuth",
    "BearerAuth",
    "AuthType",
    # Tools
    "create_a2a_tool",
    "create_a2a_tools",
]
