"""
Tools 模块

对外暴露（业务侧使用）：
- tool: 装饰器，定义工具

框架内部（业务侧不应直接使用）：
- ToolRegistry: 工具注册中心
- resolve_tools: 工具解析
- create_delegation_tool: 委派工具创建
"""

from datapillar_oneagentic.tools.delegation import (
    create_delegation_tool,
    create_delegation_tools,
)
from datapillar_oneagentic.tools.registry import ToolRegistry, resolve_tools, tool

# 只暴露业务侧 API
__all__ = [
    "tool",
]
