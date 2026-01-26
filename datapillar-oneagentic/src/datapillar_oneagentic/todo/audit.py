"""
Todo auditor.

Uses the LLM to align Todo progress when no updates were reported.
"""

from __future__ import annotations

import json
import logging
from typing import Any

from datapillar_oneagentic.context import ContextBuilder
from pydantic import BaseModel, Field

from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.todo.session_todo import SessionTodoList, TodoUpdate
from datapillar_oneagentic.utils.prompt_format import format_code_block, format_markdown
from datapillar_oneagentic.utils.structured_output import parse_structured_output

logger = logging.getLogger(__name__)


class TodoAuditOutput(BaseModel):
    """Todo audit output."""

    updates: list[TodoUpdate] = Field(default_factory=list, description="Todo updates")
    reason: str = Field(default="", description="Audit note")


def _parse_audit_output(result: Any) -> TodoAuditOutput:
    """Parse Todo audit output (strict mode)."""
    return parse_structured_output(result, TodoAuditOutput, strict=False)


TODO_AUDIT_OUTPUT_SCHEMA = """{
  "updates": [
    {"id": "t1", "status": "completed", "result": "short result"}
  ],
  "reason": "audit note"
}"""

TODO_AUDIT_PROMPT = format_markdown(
    title=None,
    sections=[
        ("Role", "You are a todo auditor that aligns todo status with execution results."),
        (
            "Rules",
            [
                "Use only observed results; do not speculate.",
                "You may update multiple items or none.",
                "Status must be: pending / running / completed / failed / skipped.",
                "Keep result short and factual.",
            ],
        ),
        ("Output (JSON)", format_code_block("json", TODO_AUDIT_OUTPUT_SCHEMA)),
    ],
)


def _normalize_deliverable(deliverable: Any) -> str:
    """Normalize deliverable into readable text."""
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
    Audit Todo updates.

    Args:
        todo: Current Todo list
        agent_status: Agent execution status
        deliverable: Deliverable payload
        error: Error message (optional)
        llm: LLM instance

    Returns:
        TodoUpdate list (may be empty)
    """
    if not todo.items:
        return []

    deliverable_text = _normalize_deliverable(deliverable)
    error_text = error or ""

    status_value = agent_status.value if hasattr(agent_status, "value") else agent_status
    context_sections: list[tuple[str, str | list[str]]] = [
        ("Todo", todo.to_prompt(include_title=False)),
        ("Execution Status", status_value),
    ]
    if deliverable_text:
        context_sections.append(("Deliverable Summary", deliverable_text[:2000]))
    if error_text:
        context_sections.append(("Error", error_text[:500]))

    context = format_markdown(title=None, sections=context_sections)

    messages = ContextBuilder.build_todo_audit(
        system_prompt=TODO_AUDIT_PROMPT,
        context=context,
    )

    logger.info("Todo audit started")
    structured_llm = llm.with_structured_output(
        TodoAuditOutput,
        method="function_calling",
        include_raw=True,
    )
    result = await structured_llm.ainvoke(messages)
    output = _parse_audit_output(result)

    if output.updates:
        logger.info(f"Todo audit updates: {len(output.updates)}")
    else:
        logger.info("Todo audit has no updates")

    return output.updates
