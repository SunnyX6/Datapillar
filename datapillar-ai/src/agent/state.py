"""
Multi-Agent工作流状态定义
参考 examples/context01.py 的设计原则
"""

import operator
from typing import Optional, Annotated, Sequence
from pydantic import BaseModel, Field
from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages

# Schema 类型仅用于 Agent 内部逻辑，State 中统一使用 dict


class OrchestratorState(BaseModel):
    """
    Multi-Agent系统的全局共享状态

    Checkpoint 设计原则（参考 examples/context01.py）：
    1. messages 使用 RemoveMessage 物理删除旧消息
    2. 摘要用 SystemMessage 存储在 messages 中
    """

    # ==================== 核心对话数据（checkpoint 重点）====================
    # 消息列表（使用 add_messages reducer 处理 RemoveMessage 物理删除，摘要用 SystemMessage）
    messages: Annotated[Sequence[BaseMessage], add_messages] = Field(default_factory=list)

    # ==================== 会话元信息 ====================
    session_id: str = Field(default="")
    user_id: str = Field(default="")
    user_input: str = Field(default="")

    # ==================== 执行控制 ====================
    next: str = Field(default="")
    current_agent: Optional[str] = Field(default=None)

    # ==================== 中间状态（体积小，可以保存）====================
    # 🔥 关键：Agent 返回 dict，State 也必须定义为 dict（参考 examples/context01.py）
    requirement: Optional[dict] = Field(default=None)
    query_result: Optional[dict] = Field(default=None)
    plan: Optional[dict] = Field(default=None)
    workflow: Optional[dict] = Field(default=None)
    selected_tools: Optional[list[str]] = Field(default=None)
    chat_response: Optional[str] = Field(default=None)
    is_found: Optional[bool] = Field(default=None)
    error: Optional[str] = Field(default=None)

    class Config:
        arbitrary_types_allowed = True  # 允许任意类型（如 BaseMessage）
