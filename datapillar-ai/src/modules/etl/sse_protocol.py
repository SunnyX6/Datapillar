"""
ETL SSE 协议适配器（v3）

职责：
- 将底层 SSE 事件转换为前端协议事件（stream）
- 事件结构稳定，前端只更新同一条消息
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
    message_id: str
    replied: bool = False
    last_event_id: int = 0
    deliverables: dict[str, Any] = field(default_factory=dict)
    developer_done: bool = False
    workflow_payload: dict[str, Any] | None = None


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


def _ensure_dict(value: Any) -> dict[str, Any] | None:
    if isinstance(value, dict):
        return value
    if hasattr(value, "model_dump"):
        return value.model_dump()
    if hasattr(value, "dict"):
        return value.dict()
    return None


def _build_stream_event(
    *,
    run_id: str,
    message_id: str,
    status: str,
    phase: str | None = None,
    activity: dict[str, Any] | None = None,
    message: str = "",
    interrupt: dict[str, Any] | None = None,
    workflow: dict[str, Any] | None = None,
    recommendations: list[str] | None = None,
) -> dict[str, Any]:
    event = {
        "v": 3,
        "ts": now_ms(),
        "event": "stream",
        "run_id": run_id,
        "message_id": message_id,
        "status": status,
        "phase": phase or "analysis",
        "activity": activity,
        "message": message,
        "interrupt": interrupt or {"text": "", "options": []},
        "workflow": workflow,
    }
    if recommendations:
        event["recommendations"] = recommendations
    return event


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


def _build_interrupt_event(state: SseRunState, payload: Any, *, phase: str | None) -> dict[str, Any]:
    message, interrupt = _normalize_interrupt_payload(payload)
    return _build_stream_event(
        run_id=state.run_id,
        message_id=state.message_id,
        status="interrupt",
        phase=phase,
        activity=_build_activity(
            phase=phase or "",
            status="waiting",
            actor=None,
            title=PHASE_TITLE.get(phase or "", phase or ""),
        )
        if phase
        else None,
        message=message,
        interrupt=interrupt,
        workflow=state.workflow_payload if state.developer_done else None,
    )


def _build_error_event(state: SseRunState, error_payload: dict[str, Any] | None, *, phase: str | None) -> dict[str, Any]:
    error_payload = error_payload or {}
    message = error_payload.get("message") or "执行失败"
    detail = error_payload.get("detail")
    if detail:
        message = f"{message}：{detail}"
    return _build_stream_event(
        run_id=state.run_id,
        message_id=state.message_id,
        status="error",
        phase=phase,
        activity=_build_activity(
            phase=phase or "",
            status="error",
            actor=None,
            title=PHASE_TITLE.get(phase or "", phase or ""),
        )
        if phase
        else None,
        message=message,
        interrupt={"text": "", "options": []},
        workflow=state.workflow_payload if state.developer_done else None,
    )


def _build_done_event(state: SseRunState) -> dict[str, Any] | None:
    workflow = state.workflow_payload if state.developer_done else None

    if state.developer_done and workflow:
        name = workflow.get("workflowName") or "工作流"
        return _build_stream_event(
            run_id=state.run_id,
            message_id=state.message_id,
            status="done",
            phase="develop",
            activity=_build_activity(
                phase="develop",
                status="done",
                actor="数据开发工程师",
                title=PHASE_TITLE.get("develop", "develop"),
            ),
            message=f"已生成工作流：{name}",
            interrupt={"text": "", "options": []},
            workflow=workflow,
        )

    if "catalog" in state.deliverables:
        message, recommendations = _extract_info_message(state.deliverables["catalog"], fallback="已完成元数据查询")
        return _build_stream_event(
            run_id=state.run_id,
            message_id=state.message_id,
            status="done",
            phase="catalog",
            activity=_build_activity(
                phase="catalog",
                status="done",
                actor="元数据专员",
                title=PHASE_TITLE.get("catalog", "catalog"),
            ),
            message=message,
            interrupt={"text": "", "options": []},
            workflow=None,
            recommendations=recommendations,
        )

    if "reviewer" in state.deliverables:
        message, _ = _extract_info_message(state.deliverables["reviewer"], fallback="评审完成")
        return _build_stream_event(
            run_id=state.run_id,
            message_id=state.message_id,
            status="done",
            phase="review",
            activity=_build_activity(
                phase="review",
                status="done",
                actor="代码评审员",
                title=PHASE_TITLE.get("review", "review"),
            ),
            message=message,
            interrupt={"text": "", "options": []},
            workflow=None,
        )

    if "developer" in state.deliverables:
        message, _ = _extract_info_message(state.deliverables["developer"], fallback="SQL 生成完成")
        return _build_stream_event(
            run_id=state.run_id,
            message_id=state.message_id,
            status="done",
            phase="develop",
            activity=_build_activity(
                phase="develop",
                status="done",
                actor="数据开发工程师",
                title=PHASE_TITLE.get("develop", "develop"),
            ),
            message=message,
            interrupt={"text": "", "options": []},
            workflow=None,
        )

    if "analyst" in state.deliverables:
        message, recommendations = _extract_info_message(state.deliverables["analyst"], fallback="需求分析完成")
        return _build_stream_event(
            run_id=state.run_id,
            message_id=state.message_id,
            status="done",
            phase="analysis",
            activity=_build_activity(
                phase="analysis",
                status="done",
                actor="需求分析师",
                title=PHASE_TITLE.get("analysis", "analysis"),
            ),
            message=message,
            interrupt={"text": "", "options": []},
            workflow=None,
            recommendations=recommendations,
        )

    return None


def _normalize_interrupt_payload(payload: Any) -> tuple[str, dict[str, Any]]:
    message = "需要补充信息才能继续。"
    questions: list[str] = []
    options: list[str] = []

    if isinstance(payload, dict):
        message = payload.get("message") or message
        questions = [str(q) for q in payload.get("questions") or [] if str(q).strip()]
        options = _extract_option_labels(payload.get("options") or [])
    elif isinstance(payload, list):
        questions = [str(q) for q in payload if str(q).strip()]
    elif isinstance(payload, str) and payload.strip():
        message = payload.strip()

    interrupt_text = _build_interrupt_text(message, questions)
    return message, {"text": interrupt_text, "options": options}


def _build_interrupt_text(message: str, questions: list[str]) -> str:
    if not questions:
        return message
    lines = [message, ""]
    lines.extend([f"{index}. {question}" for index, question in enumerate(questions, start=1)])
    return "\n".join(lines).strip()


def _extract_option_labels(options: list[Any]) -> list[str]:
    labels: list[str] = []
    for option in options:
        if isinstance(option, str):
            text = option.strip()
            if text:
                labels.append(text)
            continue
        if not isinstance(option, dict):
            continue
        label = option.get("label") or option.get("name") or option.get("value") or option.get("path")
        if label:
            labels.append(str(label))
    return labels


def _extract_info_message(deliverable: Any, *, fallback: str) -> tuple[str, list[str] | None]:
    message = fallback
    recommendations: list[str] | None = None
    data = _ensure_dict(deliverable)
    if data:
        summary = data.get("summary")
        answer = data.get("answer")
        sql = data.get("sql")
        issues = data.get("issues")
        recs = data.get("recommendations")
        if summary:
            message = summary
        elif answer:
            message = answer
        elif sql:
            message = "SQL 生成完成"
        elif isinstance(issues, list) and issues:
            message = str(issues[0])
        if isinstance(recs, list):
            recommendations = [str(item) for item in recs if str(item).strip()]
    return message, recommendations


def _build_workflow_response(deliverable: Any) -> dict[str, Any] | None:
    if deliverable is None:
        return None

    deliverable = _ensure_dict(deliverable)
    if not deliverable:
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
        return _build_stream_event(
            run_id=state.run_id,
            message_id=state.message_id,
            status="running",
            phase=phase,
            activity=_build_activity(
                phase=phase,
                status="running",
                actor=agent_name,
                title=PHASE_TITLE.get(phase, phase),
            ),
            message="",
            interrupt={"text": "", "options": []},
            workflow=state.workflow_payload if state.developer_done else None,
        )

    if event_type == "agent.end" and phase:
        deliverable = (payload.get("data") or {}).get("deliverable")
        if deliverable is not None:
            state.deliverables[agent_id] = deliverable
        if agent_id == "architect":
            workflow = _build_workflow_response(deliverable)
            if workflow:
                state.workflow_payload = workflow
        if agent_id == "developer":
            state.developer_done = True
            workflow = _build_workflow_response(deliverable)
            if workflow:
                state.workflow_payload = workflow
            elif state.workflow_payload is None and "architect" in state.deliverables:
                workflow = _build_workflow_response(state.deliverables["architect"])
                if workflow:
                    state.workflow_payload = workflow
        return _build_stream_event(
            run_id=state.run_id,
            message_id=state.message_id,
            status="running",
            phase=phase,
            activity=_build_activity(
                phase=phase,
                status="done",
                actor=agent_name,
                title=PHASE_TITLE.get(phase, phase),
            ),
            message="",
            interrupt={"text": "", "options": []},
            workflow=state.workflow_payload if state.developer_done else None,
        )

    if event_type == "agent.failed":
        if state.replied:
            return None
        error_payload = (payload.get("data") or {}).get("error")
        state.replied = True
        return _build_error_event(state, error_payload, phase=phase)

    if event_type == "agent.interrupt":
        if state.replied:
            return None
        interrupt_payload = (payload.get("data") or {}).get("interrupt", {}).get("payload")
        state.replied = True
        return _build_interrupt_event(state, interrupt_payload, phase=phase)

    return None


async def adapt_sse_stream(
    *,
    source: AsyncGenerator[dict[str, Any], None],
    run_id: str,
    on_run_complete: Callable[[], None] | None = None,
) -> AsyncGenerator[dict[str, Any], None]:
    state = SseRunState(run_id=run_id, message_id=f"msg-{run_id}")
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

        if mapped.get("event") == "stream" and mapped.get("status") in {"interrupt", "error"}:
            if on_run_complete and not for_complete_called:
                on_run_complete()
                for_complete_called = True
            yield {"id": str(event_id or state.last_event_id), "data": json.dumps(mapped, ensure_ascii=False)}
            return

        yield {"id": str(event_id or state.last_event_id), "data": json.dumps(mapped, ensure_ascii=False)}

    if state.replied:
        return

    reply = _build_done_event(state)
    if not reply:
        return

    final_id = state.last_event_id + 1 if state.last_event_id else 1
    if on_run_complete and not for_complete_called:
        on_run_complete()
    yield {"id": str(final_id), "data": json.dumps(reply, ensure_ascii=False)}
