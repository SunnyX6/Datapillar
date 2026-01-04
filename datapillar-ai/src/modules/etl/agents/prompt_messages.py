"""
统一构造 Agent Prompt messages

目标：
- 让所有 Agent 以一致方式注入：系统指令 / 任务载荷 / 知识上下文 / 记忆上下文
- 记忆上下文包含：对话历史（压缩摘要 + 最近几轮）+ 需求 TODO
"""

from __future__ import annotations

import json
from typing import Any

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage

from src.modules.etl.context import (
    CompressionReason,
    CompressionScope,
    ContextBudget,
    clip_payload,
    decide_compression_trigger,
    get_default_budget,
)
from src.shared.config.settings import settings


def build_llm_messages(
    *,
    system_instructions: str,
    agent_id: str,
    user_query: str,
    task_payload: dict[str, Any] | None = None,
    context_payload: dict[str, Any] | None = None,
    memory_context: dict[str, Any] | None = None,
    context_budget: ContextBudget | None = None,
    force_clipping: bool = False,
    forced_scope: str | None = None,
) -> list[BaseMessage]:
    """
    构建 LLM 消息列表

    参数：
    - system_instructions: 系统指令
    - agent_id: Agent ID
    - user_query: 用户查询
    - task_payload: 任务载荷（可选）
    - context_payload: 知识上下文（etl_pointers 等，可选）
    - memory_context: 记忆上下文（对话历史 + 需求 TODO，可选）
    - context_budget: 上下文预算（可选）
    - force_clipping: 是否强制裁剪
    - forced_scope: 强制裁剪范围
    """
    full_messages = _build_messages(
        system_instructions=system_instructions,
        user_query=user_query,
        task_payload=task_payload,
        context_payload=context_payload,
        memory_context=memory_context,
    )

    budget = context_budget or get_default_budget()
    decision = (
        decide_compression_trigger(user_input=user_query, messages=full_messages, budget=budget)
        if not force_clipping
        else None
    )
    if (decision is None) and (not force_clipping):
        return full_messages
    if (decision is not None) and (decision.reason == CompressionReason.manual):
        return full_messages

    max_string_chars = int(settings.get("etl_llm_payload_max_string_chars", 4000))
    max_list_items = int(settings.get("etl_llm_payload_max_list_items", 200))
    max_dict_items = int(settings.get("etl_llm_payload_max_dict_items", 200))

    scope = (
        CompressionScope(forced_scope)
        if force_clipping
        and isinstance(forced_scope, str)
        and forced_scope in {s.value for s in CompressionScope}
        else (decision.scope if decision is not None else CompressionScope.both)
    )

    compact_task_payload = (
        clip_payload(
            task_payload,
            max_string_chars=max_string_chars,
            max_list_items=max_list_items,
            max_dict_items=max_dict_items,
        )
        if task_payload is not None and scope in {CompressionScope.artifacts, CompressionScope.both}
        else task_payload
    )
    compact_context_payload = (
        clip_payload(
            context_payload,
            max_string_chars=max_string_chars,
            max_list_items=max_list_items,
            max_dict_items=max_dict_items,
        )
        if context_payload is not None
        and scope in {CompressionScope.requirement, CompressionScope.both}
        else context_payload
    )
    compact_memory_context = (
        clip_payload(
            memory_context,
            max_string_chars=max_string_chars,
            max_list_items=max_list_items,
            max_dict_items=max_dict_items,
        )
        if memory_context is not None
        and scope in {CompressionScope.requirement, CompressionScope.both}
        else memory_context
    )
    return _build_messages(
        system_instructions=system_instructions,
        user_query=user_query,
        task_payload=compact_task_payload if compact_task_payload is not None else None,
        context_payload=compact_context_payload if compact_context_payload is not None else None,
        memory_context=compact_memory_context if compact_memory_context is not None else None,
    )


def _build_messages(
    *,
    system_instructions: str,
    user_query: str,
    task_payload: Any | None,
    context_payload: Any | None,
    memory_context: Any | None = None,
) -> list[BaseMessage]:
    messages: list[BaseMessage] = [SystemMessage(content=system_instructions)]

    if task_payload is not None:
        messages.append(
            SystemMessage(content=json.dumps(task_payload, ensure_ascii=False, default=str))
        )

    if context_payload is not None:
        messages.append(
            SystemMessage(content=json.dumps(context_payload, ensure_ascii=False, default=str))
        )

    if memory_context is not None:
        # 记忆上下文包含对话历史和需求 TODO
        messages.append(SystemMessage(content=_format_memory_context(memory_context)))

    messages.append(HumanMessage(content=user_query))
    return messages


def _format_memory_context(memory_context: dict[str, Any]) -> str:
    """格式化记忆上下文为可读文本"""
    lines = ["## 会话记忆"]

    # 对话历史
    conversation = memory_context.get("conversation", {})
    compressed_summary = conversation.get("compressed_summary", "")
    recent_turns = conversation.get("recent_turns", [])

    if compressed_summary:
        lines.append(f"\n### 历史对话摘要\n{compressed_summary}")

    if recent_turns:
        lines.append("\n### 最近对话")
        for turn in recent_turns:
            role = "用户" if turn.get("role") == "user" else "助手"
            content = turn.get("content", "")[:500]
            lines.append(f"- {role}: {content}")

    # 需求 TODO
    todos = memory_context.get("requirement_todos", [])
    if todos:
        lines.append("\n### 需求 TODO")
        for todo in todos:
            status = "✅" if todo.get("status") == "done" else "⏳"
            lines.append(f"- {status} {todo.get('title', '')}")

    return "\n".join(lines)
