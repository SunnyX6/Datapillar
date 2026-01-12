"""
Core 模块

对外暴露（业务侧使用）：
- agent: 装饰器
- AgentContext: 执行上下文
- AgentRole: Agent 角色
- Clarification: 澄清请求

框架内部（业务侧不应直接使用）：
- AgentSpec: Agent 规格
- AgentRegistry: Agent 注册中心
- AgentResult: 执行结果
"""

# === 业务侧 API ===
# === 框架内部（不在 __all__ 中，但仍可导入）===
from src.modules.oneagentic.core.agent import AgentRegistry, AgentSpec, agent
from src.modules.oneagentic.core.context import AgentContext
from src.modules.oneagentic.core.types import AgentResult, AgentRole, Clarification

__all__ = [
    # 业务侧 API
    "agent",
    "AgentContext",
    "AgentRole",
    "Clarification",
]
