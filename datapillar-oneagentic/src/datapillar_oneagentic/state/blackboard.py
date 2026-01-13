"""
Blackboard - 图状态

LangGraph StateGraph 的状态定义。

设计原则：
- 使用 TypedDict 支持 LangGraph reducer
- 所有会话级状态由 Checkpointer 持久化
- active_agent=None 表示流程结束
- 短期记忆通过 messages 字段实现（LangGraph 标准）
- timeline 记录完整执行历史，支持时间旅行
- deliverables 存储在 Store 中，state 只存 keys 引用
"""

from __future__ import annotations

from typing import Annotated

from langchain_core.messages import AnyMessage
from langgraph.graph.message import add_messages
from typing_extensions import TypedDict


class Blackboard(TypedDict, total=False):
    """
    Blackboard - 图状态

    核心字段：
    - messages: 对话消息列表（短期记忆，LangGraph 标准，通过 add_messages reducer 自动合并）
    - session_id: 会话标识
    - active_agent: 当前活跃的 Agent ID
    - deliverable_keys: 已产出的交付物 key 列表（实际内容存在 Store）
    - timeline: 执行时间线（Timeline.model_dump()）

    ReAct 模式字段：
    - goal: 用户目标
    - plan: 执行计划（Plan.model_dump()）
    - reflection: 反思结果（Reflection.model_dump()）
    """

    # 对话消息（短期记忆，Agent 间通过此字段自动共享上下文）
    messages: Annotated[list[AnyMessage], add_messages]

    # 会话标识
    namespace: str
    session_id: str

    # Agent 控制
    active_agent: str | None

    # 执行时间线（支持时间旅行）
    timeline: dict | None

    # 交付物 keys（实际内容存在 Store 中）
    deliverable_keys: list[str]
    deliverable_versions: dict[str, int]

    # 经验上下文（框架自动检索注入）
    experience_context: str | None

    # Agent 执行状态
    last_agent_status: str | None
    last_agent_error: str | None

    # Clarification 中断状态（两轮流程：第一轮写入问题，第二轮 interrupt 等待回答）
    pending_clarification: dict | None

    # ReAct 模式
    goal: str | None
    plan: dict | None
    reflection: dict | None
    error_retry_count: int


def create_blackboard(
    *,
    namespace: str = "",
    session_id: str = "",
    experience_context: str | None = None,
) -> Blackboard:
    """创建新的 Blackboard"""
    return Blackboard(
        messages=[],
        namespace=namespace,
        session_id=session_id,
        active_agent=None,
        timeline=None,
        deliverable_keys=[],
        deliverable_versions={},
        experience_context=experience_context,
        last_agent_status=None,
        last_agent_error=None,
        pending_clarification=None,
        # ReAct 模式
        goal=None,
        plan=None,
        reflection=None,
        error_retry_count=0,
    )
