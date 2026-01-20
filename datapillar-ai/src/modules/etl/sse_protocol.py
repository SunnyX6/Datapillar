"""
ETL SSE 协议适配器（v2）

职责：
- 将底层 SSE 事件转换为前端协议事件（process/reply）
- 统一 reply 结构，避免前端解析 agent/tool
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, AsyncGenerator, Callable

from datapillar_oneagentic.utils.time import now_ms


PROCESS_PHASE_BY_AGENT = {
    "analyst": "analysis",
    "catalog": "catalog",
    "architect": "design",
    "developer": "develop",
    "reviewer": "review",
}

PHASE_TITLE = {
    "analysis": "需求分析",
    "catalog": "元数据检索",
    "design": "架构设计",
    "develop": "SQL/作业生成",
    "review": "质量评审",
}

STATUS_DETAIL = {
    "running": "阶段进行中",
    "waiting": "等待补充信息",
    "done": "阶段完成",
    "error": "阶段失败",
    "aborted": "阶段已停止",
}


@dataclass
class SseRunState:
    run_id: str
    replied: bool = False
    last_event_id: int = 0
    deliverables: dict[str, Any] = field(default_factory=dict)


class RunRegistry:
    def __init__(self, *, ttl_seconds: int = 60 * 60) -> None:
        self._ttl_seconds = ttl_seconds
        self._runs: dict[str, tuple[str, int]] = {}

    def start_run(self, key: str) -> str:
        run_id = f"run-{now_ms()}"
        self._runs[key] = (run_id, now_ms())
        return run_id

    def get_run(self, key: str) -> str | None:
        data = self._runs.get(key)
        if not data:
            return None
        run_id, created_at = data
        if now_ms() - created_at > self._ttl_seconds * 1000:
            self._runs.pop(key, None)
            return None
        return run_id

    def finish_run(self, key: str) -> None:
        self._runs.pop(key, None)


def _parse_event_id(raw_id: Any) -> int:
    if raw_id is None:
        return 0
    try:
        return int(raw_id)
    except (TypeError, ValueError):
        return 0


def _parse_payload(raw_data: Any) -> dict[str, Any] | None:
    if raw_data is None:
        return None
    if isinstance(raw_data, dict):
        return raw_data
    if isinstance(raw_data, str):
        try:
            return json.loads(raw_data)
        except json.JSONDecodeError:
            return None
    return None


def _build_process_event(run_id: str, activity: dict[str, Any]) -> dict[str, Any]:
    return {
        "v": 2,
        "ts": now_ms(),
        "event": "process",
        "run_id": run_id,
        "activity": activity,
    }


def _build_reply_event(run_id: str, reply: dict[str, Any]) -> dict[str, Any]:
    return {
        "v": 2,
        "ts": now_ms(),
        "event": "reply",
        "run_id": run_id,
        "reply": reply,
    }


def _build_activity(
    *,
    phase: str,
    status: str,
    actor: str | None,
    title: str | None = None,
    detail: str | None = None,
) -> dict[str, Any]:
    return {
        "id": f"phase:{phase}",
        "phase": phase,
        "status": status,
        "actor": actor or "",
        "title": title or PHASE_TITLE.get(phase, phase),
        "detail": detail or STATUS_DETAIL.get(status, ""),
    }


def _build_reply_waiting(payload: Any) -> dict[str, Any]:
    message, ui_payload = _normalize_interrupt_payload(payload)
    return {
        "status": "waiting",
        "message": message,
        "render": {"type": "ui", "schema": "v1"},
        "payload": ui_payload,
    }


def _build_reply_error(error_payload: dict[str, Any] | None) -> dict[str, Any]:
    error_payload = error_payload or {}
    message = error_payload.get("message") or "执行失败"
    detail = error_payload.get("detail")
    if detail:
        message = f"{message}：{detail}"
    return {
        "status": "error",
        "message": message,
        "render": {"type": "ui", "schema": "v1"},
        "payload": {
            "kind": "info",
            "level": "error",
            "items": [message],
        },
    }


def _build_reply_done(state: SseRunState) -> dict[str, Any] | None:
    if "architect" in state.deliverables:
        workflow = _build_workflow_response(state.deliverables["architect"])
        if workflow:
            name = workflow.get("workflowName") or "工作流"
            return {
                "status": "done",
                "message": f"已生成工作流：{name}",
                "render": {"type": "workflow", "schema": "workflow_response.v1"},
                "payload": workflow,
            }

    if "catalog" in state.deliverables:
        return _build_info_reply(state.deliverables["catalog"], fallback="已完成元数据查询")

    if "reviewer" in state.deliverables:
        return _build_info_reply(state.deliverables["reviewer"], fallback="评审完成")

    if "developer" in state.deliverables:
        return _build_info_reply(state.deliverables["developer"], fallback="SQL 生成完成")

    if "analyst" in state.deliverables:
        return _build_info_reply(state.deliverables["analyst"], fallback="需求分析完成")

    return None


def _build_info_reply(deliverable: Any, *, fallback: str) -> dict[str, Any]:
    message = fallback
    items: list[str] = []
    if isinstance(deliverable, dict):
        summary = deliverable.get("summary")
        answer = deliverable.get("answer")
        sql = deliverable.get("sql")
        issues = deliverable.get("issues")
        if summary:
            message = summary
            items.append(summary)
        if answer:
            items.append(answer)
        if sql:
            items.append(sql)
        if isinstance(issues, list):
            items.extend([str(item) for item in issues])
    return {
        "status": "done",
        "message": message,
        "render": {"type": "ui", "schema": "v1"},
        "payload": {
            "kind": "info",
            "level": "info",
            "items": items or [message],
        },
    }


def _normalize_interrupt_payload(payload: Any) -> tuple[str, dict[str, Any]]:
    if isinstance(payload, dict):
        if payload.get("kind") in {"form", "actions", "info"}:
            message = payload.get("message") or "需要补充信息才能继续。"
            return message, payload
        message = payload.get("message") or "需要补充信息才能继续。"
        questions = payload.get("questions") or []
        options = payload.get("options") or []
        if options:
            return message, _build_actions_payload(options)
        if questions:
            return message, _build_form_payload(questions)
        return message, {"kind": "info", "level": "info", "items": [message]}

    if isinstance(payload, list):
        return "需要补充信息才能继续。", _build_form_payload(payload)

    if isinstance(payload, str):
        return payload, {"kind": "info", "level": "info", "items": [payload]}

    return "需要补充信息才能继续。", {"kind": "info", "level": "info", "items": ["需要补充信息才能继续。"]}


def _build_form_payload(questions: list[Any]) -> dict[str, Any]:
    fields = []
    for index, question in enumerate(questions, start=1):
        label = str(question).strip()
        if not label:
            continue
        fields.append(
            {
                "id": f"q{index}",
                "label": label,
                "type": "text",
                "required": True,
            }
        )
    return {
        "kind": "form",
        "fields": fields,
        "submit": {"label": "确认并继续"},
    }


def _build_actions_payload(options: list[Any]) -> dict[str, Any]:
    actions = []
    for option in options:
        if not isinstance(option, dict):
            continue
        label = option.get("name") or option.get("label") or option.get("value") or "选项"
        action_type = option.get("type") or "button"
        if action_type == "link":
            url = option.get("url") or option.get("path") or option.get("value") or ""
            if url:
                actions.append({"type": "link", "label": label, "url": url})
            continue
        value = option.get("value") or option.get("path") or option.get("name") or label
        actions.append({"type": "button", "label": label, "value": value})
    return {"kind": "actions", "actions": actions}


def _build_workflow_response(deliverable: Any) -> dict[str, Any] | None:
    if deliverable is None:
        return None

    if not isinstance(deliverable, dict):
        return None

    jobs = deliverable.get("jobs") or []
    if not isinstance(jobs, list):
        jobs = []

    job_id_map: dict[str, int] = {}
    job_responses: list[dict[str, Any]] = []
    for index, job in enumerate(jobs, start=1):
        if not isinstance(job, dict):
            continue
        raw_id = str(job.get("id") or f"job_{index}")
        job_id_map[raw_id] = index
        job_responses.append(
            {
                "id": index,
                "jobName": job.get("name") or raw_id,
                "jobType": None,
                "jobTypeCode": "SQL",
                "jobTypeName": "SQL",
                "jobParams": {
                    "stages": job.get("stages") or [],
                    "inputTables": job.get("input_tables") or [],
                    "outputTable": job.get("output_table"),
                },
                "timeoutSeconds": 3600,
                "maxRetryTimes": 3,
                "retryInterval": 60,
                "priority": 0,
                "positionX": (index - 1) * 240,
                "positionY": 0,
                "description": job.get("description"),
            }
        )

    dependencies: list[dict[str, Any]] = []
    for job in jobs:
        if not isinstance(job, dict):
            continue
        raw_id = str(job.get("id"))
        current_id = job_id_map.get(raw_id)
        if not current_id:
            continue
        depends = job.get("depends") or []
        if not isinstance(depends, list):
            continue
        for dep in depends:
            parent_id = job_id_map.get(str(dep))
            if not parent_id:
                continue
            dependencies.append({"jobId": current_id, "parentJobId": parent_id})

    return {
        "workflowName": deliverable.get("name") or "未命名工作流",
        "triggerType": 0,
        "triggerValue": None,
        "timeoutSeconds": 3600,
        "maxRetryTimes": 3,
        "priority": 0,
        "description": deliverable.get("description"),
        "jobs": job_responses,
        "dependencies": dependencies,
    }


def _map_payload(payload: dict[str, Any], state: SseRunState) -> dict[str, Any] | None:
    event_type = payload.get("event")
    agent = payload.get("agent") or {}
    agent_id = agent.get("id") or ""
    agent_name = agent.get("name") or agent_id
    phase = PROCESS_PHASE_BY_AGENT.get(agent_id)

    if event_type == "agent.start" and phase:
        return _build_process_event(
            state.run_id,
            _build_activity(
                phase=phase,
                status="running",
                actor=agent_name,
                title=PHASE_TITLE.get(phase, phase),
            ),
        )

    if event_type == "agent.end" and phase:
        deliverable = (payload.get("data") or {}).get("deliverable")
        if deliverable is not None:
            state.deliverables[agent_id] = deliverable
        return _build_process_event(
            state.run_id,
            _build_activity(
                phase=phase,
                status="done",
                actor=agent_name,
                title=PHASE_TITLE.get(phase, phase),
            ),
        )

    if event_type == "agent.failed":
        if state.replied:
            return None
        error_payload = (payload.get("data") or {}).get("error")
        state.replied = True
        return _build_reply_event(state.run_id, _build_reply_error(error_payload))

    if event_type == "agent.interrupt":
        if state.replied:
            return None
        interrupt_payload = (payload.get("data") or {}).get("interrupt", {}).get("payload")
        state.replied = True
        return _build_reply_event(state.run_id, _build_reply_waiting(interrupt_payload))

    return None


async def adapt_sse_stream(
    *,
    source: AsyncGenerator[dict[str, Any], None],
    run_id: str,
    on_run_complete: Callable[[], None] | None = None,
) -> AsyncGenerator[dict[str, Any], None]:
    state = SseRunState(run_id=run_id)
    for_complete_called = False

    async for raw in source:
        event_id = _parse_event_id(raw.get("id"))
        payload = _parse_payload(raw.get("data"))
        if payload is None:
            continue
        if event_id:
            state.last_event_id = event_id

        mapped = _map_payload(payload, state)
        if not mapped:
            continue

        if mapped.get("event") == "reply":
            if on_run_complete and not for_complete_called:
                on_run_complete()
                for_complete_called = True
            yield {"id": str(event_id or state.last_event_id), "data": json.dumps(mapped, ensure_ascii=False)}
            if mapped.get("reply", {}).get("status") in {"waiting", "error", "aborted"}:
                return
            continue

        yield {"id": str(event_id or state.last_event_id), "data": json.dumps(mapped, ensure_ascii=False)}

    if state.replied:
        return

    reply = _build_reply_done(state)
    if not reply:
        return

    final_id = state.last_event_id + 1 if state.last_event_id else 1
    if on_run_complete and not for_complete_called:
        on_run_complete()
    yield {"id": str(final_id), "data": json.dumps(_build_reply_event(state.run_id, reply), ensure_ascii=False)}
