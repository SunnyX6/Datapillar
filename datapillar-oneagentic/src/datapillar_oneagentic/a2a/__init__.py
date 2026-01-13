"""
A2A (Agent-to-Agent) 协议模块

基于 Google 官方 a2a-sdk 实现，支持跨服务调用远程 Agent。

核心组件：
- A2AConfig: 远程 Agent 连接配置
- create_a2a_tool: 创建 A2A 委派工具
- create_a2a_tools: 批量创建 A2A 工具

使用示例（Agent 粒度）：
```python
from datapillar_oneagentic import agent
from datapillar_oneagentic.a2a import A2AConfig, APIKeyAuth

@agent(
    id="analyst",
    name="分析师",
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

使用示例（Team 粒度）：
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

安装 A2A 支持：
```bash
pip install datapillar-oneagentic[a2a]
```
"""

from datapillar_oneagentic.a2a.config import (
    A2AConfig,
    AuthScheme,
    APIKeyAuth,
    BearerAuth,
    AuthType,
)
from datapillar_oneagentic.a2a.tool import (
    create_a2a_tool,
    create_a2a_tools,
)

__all__ = [
    # 配置
    "A2AConfig",
    "AuthScheme",
    "APIKeyAuth",
    "BearerAuth",
    "AuthType",
    # 工具
    "create_a2a_tool",
    "create_a2a_tools",
]
