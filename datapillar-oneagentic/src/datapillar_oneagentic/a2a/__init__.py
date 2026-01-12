"""
A2A (Agent-to-Agent) 协议模块

实现 Google 提出的 A2A 协议，支持跨服务 Agent 互操作。

核心组件：
- A2AConfig: 远程 Agent 配置
- AgentCard: Agent 自描述（能力、技能、认证）
- A2AClient: 调用远程 Agent
- create_a2a_tool: 创建 A2A 委派工具
- a2a_router: A2A Server 网关路由（单独导入避免循环依赖）

使用 A2A Server:
```python
from datapillar_oneagentic.a2a.server import a2a_router
app.include_router(a2a_router)
```
"""

from datapillar_oneagentic.a2a.config import (
    A2AConfig,
    AuthScheme,
    APIKeyAuth,
    BearerAuth,
)
from datapillar_oneagentic.a2a.card import AgentCard, AgentSkill
from datapillar_oneagentic.a2a.client import (
    A2AClient,
    A2AResult,
    A2AMessage,
    TaskState,
    A2AError,
    A2AConnectionError,
    A2AAuthError,
)
from datapillar_oneagentic.a2a.tool import create_a2a_tool, create_a2a_tools_from_configs

# 注意：a2a_router 不在这里导出，避免循环导入
# 使用时请直接导入: from datapillar_oneagentic.a2a.server import a2a_router

__all__ = [
    # 配置
    "A2AConfig",
    "AuthScheme",
    "APIKeyAuth",
    "BearerAuth",
    # AgentCard
    "AgentCard",
    "AgentSkill",
    # Client
    "A2AClient",
    "A2AResult",
    "A2AMessage",
    "TaskState",
    # 异常
    "A2AError",
    "A2AConnectionError",
    "A2AAuthError",
    # 工具
    "create_a2a_tool",
    "create_a2a_tools_from_configs",
]
