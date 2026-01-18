"""
Agent 错误分类
"""

from __future__ import annotations

from enum import Enum


class AgentErrorCategory(str, Enum):
    """Agent 错误分类"""

    BUSINESS = "business"  # 业务失败（输入/任务本身问题）
    SYSTEM = "system"  # 系统失败（运行时异常）
    PROTOCOL = "protocol"  # 协议/契约错误（类型/返回值/配置）
    DEPENDENCY = "dependency"  # 依赖错误（外部工具/网络等）
