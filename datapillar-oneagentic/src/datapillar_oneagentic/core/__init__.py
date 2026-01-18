"""
Core 模块

对外暴露（业务侧使用）：
- agent: 装饰器
- AgentContext: 执行上下文
- Process: 执行模式
- Datapillar: 团队组织

框架内部（业务侧不应直接使用）：
- AgentSpec: Agent 规格
- AgentResult: 执行结果
"""

from __future__ import annotations

from typing import TYPE_CHECKING

__all__ = [
    "agent",
    "AgentContext",
    "Process",
    "Datapillar",
]

if TYPE_CHECKING:
    from datapillar_oneagentic.core.agent import AgentSpec, agent
    from datapillar_oneagentic.core.context import AgentContext
    from datapillar_oneagentic.core.datapillar import Datapillar
    from datapillar_oneagentic.core.process import Process
    from datapillar_oneagentic.core.types import AgentResult


def __getattr__(name: str):
    if name == "agent":
        from datapillar_oneagentic.core.agent import agent as _agent

        return _agent
    if name == "AgentContext":
        from datapillar_oneagentic.core.context import AgentContext as _AgentContext

        return _AgentContext
    if name == "Process":
        from datapillar_oneagentic.core.process import Process as _Process

        return _Process
    if name == "Datapillar":
        from datapillar_oneagentic.core.datapillar import Datapillar as _Datapillar

        return _Datapillar
    if name == "AgentSpec":
        from datapillar_oneagentic.core.agent import AgentSpec as _AgentSpec

        return _AgentSpec
    if name == "AgentResult":
        from datapillar_oneagentic.core.types import AgentResult as _AgentResult

        return _AgentResult
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
