"""
Tools 模块

业务侧使用：
- tool: 工具定义装饰器（自动注册）

框架内部：
- ToolRegistry: 工具注册中心
- resolve_tools: 解析工具
"""

from src.modules.oneagentic.tools.delegation import (
    create_delegation_tool,
    create_delegation_tools,
)
from src.modules.oneagentic.tools.registry import ToolRegistry, resolve_tools, tool

__all__ = [
    # 业务侧 API
    "tool",
]
