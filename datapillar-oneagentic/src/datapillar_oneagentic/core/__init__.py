"""
Core module.

Public API (application use):
- agent: decorator
- AgentContext: execution context
- Process: execution mode
- Datapillar: team organization

Framework internal (not for application use):
- AgentSpec: agent specification
- AgentResult: execution result
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
