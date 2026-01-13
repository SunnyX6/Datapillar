"""
Core 模块

对外暴露（业务侧使用）：
- agent: 装饰器
- AgentContext: 执行上下文
- Clarification: 澄清请求
- Process: 执行模式
- Datapillar: 团队组织
- DatapillarResult: 执行结果

框架内部（业务侧不应直接使用）：
- AgentSpec: Agent 规格
- AgentRegistry: Agent 注册中心
- AgentResult: 执行结果
"""

# === 业务侧 API ===
from datapillar_oneagentic.core.agent import agent
from datapillar_oneagentic.core.context import AgentContext
from datapillar_oneagentic.core.types import Clarification
from datapillar_oneagentic.core.process import Process
from datapillar_oneagentic.core.datapillar import Datapillar
from datapillar_oneagentic.core.result import DatapillarResult

# === 框架内部（不在 __all__ 中，但仍可导入）===
from datapillar_oneagentic.core.agent import AgentRegistry, AgentSpec
from datapillar_oneagentic.core.types import AgentResult

__all__ = [
    # 业务侧 API
    "agent",
    "AgentContext",
    "Clarification",
    "Process",
    "Datapillar",
    "DatapillarResult",
]
