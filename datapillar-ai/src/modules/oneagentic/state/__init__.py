"""
State 模块

框架内部（业务侧不应直接使用）：
- Blackboard: LangGraph 图状态
"""

# === 框架内部 ===
from src.modules.oneagentic.state.blackboard import Blackboard, create_blackboard

__all__ = []  # 不对外暴露
