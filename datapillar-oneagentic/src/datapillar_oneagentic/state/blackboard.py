# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Blackboard - graph state.

State definition for LangGraph StateGraph.

Design principles:
- Use TypedDict for LangGraph reducers
- Session-level state is persisted by Checkpointer
- active_agent=None means the flow is complete
- Short-term memory uses messages (LangGraph standard)
- timeline records full execution history and supports time travel
- deliverables are stored in Store; state only keeps key references
"""

from __future__ import annotations

import operator
from typing import Annotated, Any

from langgraph.graph.message import add_messages
from typing_extensions import TypedDict

from datapillar_oneagentic.core.status import ExecutionStatus


class Blackboard(TypedDict, total=False):
    """
    Blackboard - graph state.

    Core fields:
    - messages: chat messages (short-term memory, merged by add_messages reducer)
    - namespace: namespace
    - session_id: session identifier
    - active_agent: active agent ID
    - assigned_task: task assigned by Manager to current agent
    - deliverable_keys: deliverable keys (contents live in Store)
    - timeline: execution timeline (Timeline.model_dump())

    ReAct fields:
    - goal: user goal
    - plan: execution plan (Plan.model_dump())
    - reflection: reflection result (Reflection.model_dump())
    """

    # Chat messages (short-term memory, LangGraph reducer field).
    messages: Annotated[list[Any], add_messages]

    # Session identity
    namespace: str
    session_id: str

    # Agent control
    active_agent: str | None

    # Task assigned by Manager to current agent (hierarchical mode)
    assigned_task: str | None

    # Execution timeline (supports time travel)
    timeline: dict | None

    # Deliverable keys (contents stored in Store)
    deliverable_keys: list[str]

    # Compressed context (history summary)
    compression_context: str | None

    # Agent execution status
    last_agent_status: ExecutionStatus | None
    last_agent_error: str | None

    # Session-level Todo (team progress tracking)
    todo: dict | None

    # MapReduce mode
    mapreduce_goal: str | None
    mapreduce_understanding: str | None
    mapreduce_tasks: list[dict]
    mapreduce_task: dict | None
    mapreduce_results: Annotated[list[dict], operator.add]

    # ReAct mode
    goal: str | None
    plan: dict | None
    reflection: dict | None
    error_retry_count: int


def create_blackboard(
    *,
    namespace: str = "",
    session_id: str = "",
) -> Blackboard:
    """Create a new Blackboard."""
    return Blackboard(
        messages=[],
        namespace=namespace,
        session_id=session_id,
        active_agent=None,
        assigned_task=None,
        timeline=None,
        deliverable_keys=[],
        compression_context=None,
        last_agent_status=None,
        last_agent_error=None,
        todo=None,
        # MapReduce mode
        mapreduce_goal=None,
        mapreduce_understanding=None,
        mapreduce_tasks=[],
        mapreduce_task=None,
        mapreduce_results=[],
        # ReAct mode
        goal=None,
        plan=None,
        reflection=None,
        error_retry_count=0,
    )
