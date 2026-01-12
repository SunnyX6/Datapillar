"""
Blackboard - 图状态

LangGraph StateGraph 的状态定义。

设计原则：
- 使用 TypedDict 支持 LangGraph reducer
- 所有会话级状态由 Checkpointer 持久化
- active_agent=None 表示流程结束
- timeline 记录完整执行历史，支持时间旅行
"""

from __future__ import annotations

from typing import Annotated, Any

from langchain_core.messages import AnyMessage
from langgraph.graph.message import add_messages
from typing_extensions import TypedDict


class Blackboard(TypedDict, total=False):
    """
    Blackboard - 图状态

    核心字段：
    - messages: 对话消息列表
    - session_id, user_id: 会话标识
    - active_agent: 当前活跃的 Agent ID
    - memory: 对话记忆（ConversationMemory.model_dump()）
    - deliverables: 交付物存储
    - timeline: 执行时间线（Timeline.model_dump()）

    ReAct 模式字段：
    - goal: 用户目标
    - plan: 执行计划（Plan.model_dump()）
    - reflection: 反思结果（Reflection.model_dump()）
    """

    # 对话消息
    messages: Annotated[list[AnyMessage], add_messages]

    # 会话标识
    session_id: str
    user_id: str
    team_id: str

    # Agent 控制
    active_agent: str | None
    task_description: str | None

    # 对话记忆
    memory: dict | None

    # 执行时间线（支持时间旅行）
    timeline: dict | None

    # 交付物 {deliverable_key: deliverable}
    deliverables: dict[str, Any]

    # Agent 执行状态
    last_agent_status: str | None
    last_agent_error: str | None

    # ReAct 模式
    goal: str | None
    plan: dict | None
    reflection: dict | None
    error_retry_count: int


def create_blackboard(
    *,
    session_id: str = "",
    user_id: str = "",
    team_id: str = "",
) -> Blackboard:
    """创建新的 Blackboard"""
    return Blackboard(
        messages=[],
        session_id=session_id,
        user_id=user_id,
        team_id=team_id,
        active_agent=None,
        task_description=None,
        memory=None,
        timeline=None,
        deliverables={},
        last_agent_status=None,
        last_agent_error=None,
        # ReAct 模式
        goal=None,
        plan=None,
        reflection=None,
        error_retry_count=0,
    )
