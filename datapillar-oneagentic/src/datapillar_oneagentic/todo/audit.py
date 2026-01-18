"""
Todo 审计器

在没有上报的情况下，使用 LLM 对齐 Todo 进度。
"""

from __future__ import annotations

import json
import logging
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.todo.session_todo import SessionTodoList, TodoUpdate
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


class TodoAuditOutput(BaseModel):
    """Todo 审计输出"""

    updates: list[TodoUpdate] = Field(default_factory=list, description="需要更新的 Todo 列表")
    reason: str = Field(default="", description="审计说明")


def _parse_audit_output(result: Any) -> TodoAuditOutput:
    """解析 Todo 审计输出（带 fallback）"""
    if isinstance(result, TodoAuditOutput):
        return result

    if isinstance(result, dict):
        parsed = result.get("parsed")
        if isinstance(parsed, TodoAuditOutput):
            return parsed
        if isinstance(parsed, dict):
            return TodoAuditOutput.model_validate(parsed)

        raw = result.get("raw")
        if raw:
            content = getattr(raw, "content", None)
            if content:
                return parse_structured_output(content, TodoAuditOutput)

    raise ValueError(f"无法解析 Todo 审计输出: {type(result)}")


TODO_AUDIT_PROMPT = """你是 Todo 审计器，负责根据最新执行结果更新 Todo 状态。

## 审计原则
1. 只基于已知结果判断，不要臆测
2. 可以更新多个条目，也可以不更新
3. 状态只能是：pending / running / completed / failed / skipped
4. result 字段写简短结果，不要长篇解释

## 输出格式（必须是 JSON）
{
  "updates": [
    {"id": "t1", "status": "completed", "result": "完成的简要结果"}
  ],
  "reason": "审计说明"
}
"""


def _normalize_deliverable(deliverable: Any) -> str:
    """将交付物归一化为可读文本"""
    if deliverable is None:
        return ""
    if hasattr(deliverable, "model_dump"):
        try:
            return json.dumps(deliverable.model_dump(mode="json"), ensure_ascii=False)
        except Exception:
            return str(deliverable)
    if isinstance(deliverable, dict):
        return json.dumps(deliverable, ensure_ascii=False)
    return str(deliverable)


async def audit_todo_updates(
    *,
    todo: SessionTodoList,
    agent_status: ExecutionStatus | str,
    deliverable: Any,
    error: str | None,
    llm: Any,
) -> list[TodoUpdate]:
    """
    审计 Todo 更新

    Args:
        todo: 当前 Todo 列表
        agent_status: Agent 执行状态
        deliverable: 交付物
        error: 错误信息（可选）
        llm: LLM 实例

    Returns:
        TodoUpdate 列表（可为空）
    """
    if not todo.items:
        return []

    deliverable_text = _normalize_deliverable(deliverable)
    error_text = error or ""

    status_value = agent_status.value if hasattr(agent_status, "value") else agent_status
    context_parts = [
        todo.to_prompt(),
        f"\n## 执行状态\n{status_value}",
    ]
    if deliverable_text:
        context_parts.append(f"\n## 交付物摘要\n{deliverable_text[:2000]}")
    if error_text:
        context_parts.append(f"\n## 错误信息\n{error_text[:500]}")

    context = "\n".join(context_parts)

    messages = [
        SystemMessage(content=TODO_AUDIT_PROMPT),
        HumanMessage(content=context),
    ]

    logger.info("Todo 审计开始...")
    structured_llm = llm.with_structured_output(TodoAuditOutput, method="json_mode", include_raw=True)
    result = await structured_llm.ainvoke(messages)
    output = _parse_audit_output(result)

    if output.updates:
        logger.info(f"Todo 审计更新: {len(output.updates)} 条")
    else:
        logger.info("Todo 审计无更新")

    return output.updates
