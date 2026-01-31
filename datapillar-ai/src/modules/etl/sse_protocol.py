# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
ETL SSE 协议适配器（v3）

职责：
- 将底层 SSE 事件转换为前端协议事件（稳定字段）
- 工具调用作为独立事件输出
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, AsyncGenerator, Callable

from datapillar_oneagentic.utils.time import now_ms

from src.modules.etl.schemas.sse import (
    Activity,
    ActivityEvent,
    ActivityStatus,
    EtlSseEvent,
    RunStatus,
)


@dataclass
class SseRunState:
    run_id: str
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
    status: RunStatus,
    activity: Activity,
    workflow: dict[str, Any] | None = None,
) -> dict[str, Any]:
    event = EtlSseEvent(
        run_id=run_id,
        ts=now_ms(),
        status=status,
        activity=activity,
        workflow=workflow,
    )
    return event.model_dump(mode="json")


def _build_activity(
    *,
    agent_cn: str | None,
    agent_en: str | None,
    summary: str | None,
    status: ActivityStatus,
    event: ActivityEvent,
    event_name: str | None = None,
    interrupt: dict[str, Any] | None = None,
    recommendations: list[str] | None = None,
) -> Activity:
    if event == ActivityEvent.TOOL:
        resolved_event_name = f"invoke tool {event_name}"
    elif event == ActivityEvent.LLM:
        if status == ActivityStatus.DONE:
            resolved_event_name = "final"
        elif status == ActivityStatus.ABORTED:
            resolved_event_name = "aborted"
        else:
            resolved_event_name = "invoke llm"
    else:
        resolved_event_name = event_name or event.value
    return Activity(
        agent_cn=agent_cn ,
        agent_en=agent_en,
        summary=summary,
        event=event,
        event_name=resolved_event_name,
        status=status,
        interrupt=interrupt or {"options": []},
        recommendations=recommendations or [],
    )


def _build_interrupt_event(
    state: SseRunState,
    payload: Any,
    *,
    agent_cn: str | None,
    agent_en: str | None,
    interrupt_id: str | None = None,
) -> dict[str, Any]:
    summary, interrupt = _normalize_interrupt_payload(payload)
    if interrupt_id:
        interrupt["interrupt_id"] = interrupt_id
    return _build_stream_event(
        run_id=state.run_id,
        status=RunStatus.RUNNING,
        activity=_build_activity(
            status=ActivityStatus.WAITING,
            agent_cn=agent_cn,
            agent_en=agent_en,
            summary=summary,
            event=ActivityEvent.INTERRUPT,
            interrupt=interrupt,
        ),
        workflow=state.workflow_payload if state.developer_done else None,
    )


def _build_error_event(
    state: SseRunState,
    error_payload: dict[str, Any] | None,
    *,
    agent_cn: str | None,
    agent_en: str | None,
) -> dict[str, Any]:
    error_payload = error_payload or {}
    summary = error_payload.get("message") or "执行失败"
    detail = error_payload.get("detail")
    if detail:
        summary = f"{summary}：{detail}"
    return _build_stream_event(
        run_id=state.run_id,
        status=RunStatus.ERROR,
        activity=_build_activity(
            status=ActivityStatus.ERROR,
            agent_cn=agent_cn,
            agent_en=agent_en,
            summary=summary,
            event=ActivityEvent.LLM,
        ),
        workflow=state.workflow_payload if state.developer_done else None,
    )


def _build_done_event(state: SseRunState) -> dict[str, Any] | None:
    workflow = state.workflow_payload if state.developer_done else None

    if state.developer_done and workflow:
        name = workflow.get("workflowName") or "工作流"
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.DONE,
            activity=_build_activity(
                status=ActivityStatus.DONE,
                agent_cn="数据开发工程师",
                agent_en="developer",
                summary=f"已生成工作流：{name}",
                event=ActivityEvent.LLM,
                recommendations=[],
            ),
            workflow=workflow,
        )

    if "catalog" in state.deliverables:
        summary, recommendations = _extract_activity_summary(
            state.deliverables["catalog"],
            fallback="已完成元数据查询",
        )
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.DONE,
            activity=_build_activity(
                status=ActivityStatus.DONE,
                agent_cn="元数据专员",
                agent_en="catalog",
                summary=summary,
                event=ActivityEvent.LLM,
                recommendations=recommendations,
            ),
            workflow=None,
        )

    if "reviewer" in state.deliverables:
        summary, recommendations = _extract_activity_summary(
            state.deliverables["reviewer"],
            fallback="评审完成",
        )
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.DONE,
            activity=_build_activity(
                status=ActivityStatus.DONE,
                agent_cn="代码评审员",
                agent_en="reviewer",
                summary=summary,
                event=ActivityEvent.LLM,
                recommendations=recommendations,
            ),
            workflow=None,
        )

    if "developer" in state.deliverables:
        summary, recommendations = _extract_activity_summary(
            state.deliverables["developer"],
            fallback="SQL 生成完成",
        )
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.DONE,
            activity=_build_activity(
                status=ActivityStatus.DONE,
                agent_cn="数据开发工程师",
                agent_en="developer",
                summary=summary,
                event=ActivityEvent.LLM,
                recommendations=recommendations,
            ),
            workflow=None,
        )

    if "analyst" in state.deliverables:
        summary, recommendations = _extract_activity_summary(
            state.deliverables["analyst"],
            fallback="需求分析完成",
        )
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.DONE,
            activity=_build_activity(
                status=ActivityStatus.DONE,
                agent_cn="需求分析师",
                agent_en="analyst",
                summary=summary,
                event=ActivityEvent.LLM,
                recommendations=recommendations,
            ),
            workflow=None,
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

    summary = _build_interrupt_text(message, questions)
    return summary, {"options": options}


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


def _extract_activity_summary(deliverable: Any, *, fallback: str) -> tuple[str, list[str]]:
    summary = fallback
    recommendations: list[str] = []
    data = _ensure_dict(deliverable)
    if data:
        data_summary = data.get("summary")
        answer = data.get("answer")
        sql = data.get("sql")
        issues = data.get("issues")
        recs = data.get("recommendations")
        if data_summary:
            summary = data_summary
        elif answer:
            summary = answer
        elif sql:
            summary = "SQL 生成完成"
        elif isinstance(issues, list) and issues:
            summary = str(issues[0])
        if isinstance(recs, list):
            recommendations = [str(item) for item in recs if str(item).strip()]
    return summary, recommendations


def _normalize_pipeline_jobs(pipelines: list[dict[str, Any]]) -> list[dict[str, Any]]:
    jobs: list[dict[str, Any]] = []
    pipeline_jobs: dict[str, list[dict[str, Any]]] = {}
    job_by_id: dict[str, dict[str, Any]] = {}
    pipeline_targets: dict[str, dict[str, list[str]]] = {}
    pipeline_roots: dict[str, list[str]] = {}
    pipeline_leaves: dict[str, list[str]] = {}

    def _safe_list(value: Any) -> list[Any]:
        if isinstance(value, list):
            return value
        return []

    def _add_dep(job: dict[str, Any], dep_id: str) -> None:
        if not dep_id:
            return
        deps = job.setdefault("depends", [])
        if not isinstance(deps, list):
            deps = []
            job["depends"] = deps
        if dep_id not in deps:
            deps.append(dep_id)

    for pipeline in pipelines:
        if not isinstance(pipeline, dict):
            continue
        pipeline_id = str(pipeline.get("pipeline_id") or "")
        raw_jobs = _safe_list(pipeline.get("jobs"))
        pipeline_jobs[pipeline_id] = []
        pipeline_targets[pipeline_id] = {}

        for raw_job in raw_jobs:
            if not isinstance(raw_job, dict):
                continue
            job_id = str(raw_job.get("job_id") or "")
            if not job_id:
                continue
            source_tables = _safe_list(raw_job.get("source_tables"))
            depends_on = _safe_list(raw_job.get("depends_on"))
            target_table = raw_job.get("target_table")
            stages = _safe_list(raw_job.get("stages"))
            job = {
                "id": job_id,
                "name": raw_job.get("job_name") or job_id,
                "description": raw_job.get("description"),
                "depends": [str(dep) for dep in depends_on if str(dep).strip()],
                "stages": stages,
                "input_tables": source_tables,
                "output_table": target_table,
            }
            pipeline_jobs[pipeline_id].append(job)
            jobs.append(job)
            job_by_id[job_id] = job

            if target_table:
                pipeline_targets[pipeline_id].setdefault(str(target_table), []).append(job_id)

        job_ids = [job.get("id") for job in pipeline_jobs[pipeline_id] if job.get("id")]
        depends_set: set[str] = set()
        root_ids: list[str] = []
        for job in pipeline_jobs[pipeline_id]:
            deps = _safe_list(job.get("depends"))
            if not deps and job.get("id"):
                root_ids.append(job["id"])
            for dep in deps:
                depends_set.add(str(dep))
        pipeline_roots[pipeline_id] = root_ids
        pipeline_leaves[pipeline_id] = [job_id for job_id in job_ids if job_id not in depends_set]

    for pipeline in pipelines:
        if not isinstance(pipeline, dict):
            continue
        pipeline_id = str(pipeline.get("pipeline_id") or "")
        if pipeline_id not in pipeline_jobs:
            continue
        depends_pipelines = _safe_list(pipeline.get("depends_on_pipelines"))
        if not depends_pipelines:
            continue

        for upstream_id in depends_pipelines:
            upstream_id = str(upstream_id)
            if not upstream_id or upstream_id not in pipeline_targets:
                continue
            matched = False
            upstream_targets = pipeline_targets.get(upstream_id, {})
            for job in pipeline_jobs[pipeline_id]:
                source_tables = _safe_list(job.get("input_tables"))
                for source_table in source_tables:
                    source_table = str(source_table)
                    upstream_job_ids = upstream_targets.get(source_table) or []
                    if upstream_job_ids:
                        matched = True
                        for upstream_job_id in upstream_job_ids:
                            _add_dep(job, upstream_job_id)

            if not matched:
                for root_job_id in pipeline_roots.get(pipeline_id, []):
                    root_job = job_by_id.get(root_job_id)
                    if not root_job:
                        continue
                    for upstream_job_id in pipeline_leaves.get(upstream_id, []):
                        _add_dep(root_job, upstream_job_id)

    return jobs


def _build_workflow_response(deliverable: Any) -> dict[str, Any] | None:
    if deliverable is None:
        return None

    deliverable = _ensure_dict(deliverable)
    if not deliverable:
        return None

    jobs: list[dict[str, Any]] = []
    raw_jobs = deliverable.get("jobs")
    if isinstance(raw_jobs, list):
        jobs = raw_jobs
    elif isinstance(deliverable.get("pipelines"), list):
        jobs = _normalize_pipeline_jobs(deliverable.get("pipelines") or [])

    if not isinstance(jobs, list):
        jobs = []

    workflow_name = (
        deliverable.get("name")
        or deliverable.get("summary")
        or "未命名工作流"
    )
    workflow_description = deliverable.get("description") or deliverable.get("summary")

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
        "workflowName": workflow_name,
        "triggerType": 0,
        "triggerValue": None,
        "timeoutSeconds": 3600,
        "maxRetryTimes": 3,
        "priority": 0,
        "description": workflow_description,
        "jobs": job_responses,
        "dependencies": dependencies,
    }


def _map_payload(payload: dict[str, Any], state: SseRunState) -> dict[str, Any] | None:
    event_type = payload.get("event")
    agent = payload.get("agent") or {}
    agent_id = agent.get("id") or ""
    agent_name = agent.get("name") or agent_id
    workflow = state.workflow_payload if state.developer_done else None

    if event_type == "agent.start":
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.RUNNING,
            activity=_build_activity(
                status=ActivityStatus.RUNNING,
                agent_cn=agent_name,
                agent_en=agent_id,
                summary="",
                event=ActivityEvent.LLM,
            ),
            workflow=workflow,
        )

    if event_type == "agent.end":
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
        summary, recommendations = _extract_activity_summary(
            deliverable,
            fallback="",
        ) if deliverable is not None else ("", [])
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.RUNNING,
            activity=_build_activity(
                status=ActivityStatus.DONE,
                agent_cn=agent_name,
                agent_en=agent_id,
                summary=summary,
                event=ActivityEvent.LLM,
                recommendations=recommendations,
            ),
            workflow=workflow,
        )

    if event_type == "agent.failed":
        error_payload = (payload.get("data") or {}).get("error")
        return _build_error_event(
            state,
            error_payload,
            agent_cn=agent_name,
            agent_en=agent_id,
        )

    if event_type == "agent.interrupt":
        interrupt_data = (payload.get("data") or {}).get("interrupt", {}) or {}
        interrupt_payload = interrupt_data.get("payload")
        interrupt_id = interrupt_data.get("interrupt_id")
        return _build_interrupt_event(
            state,
            interrupt_payload,
            agent_cn=agent_name,
            agent_en=agent_id,
            interrupt_id=interrupt_id if isinstance(interrupt_id, str) else None,
        )

    if event_type == "session.abort":
        abort_payload = (payload.get("data") or {}).get("abort") or {}
        summary = abort_payload.get("message") or "已终止"
        detail = abort_payload.get("detail")
        if detail:
            summary = f"{summary}：{detail}"
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.ABORTED,
            activity=_build_activity(
                status=ActivityStatus.ABORTED,
                agent_cn=agent_name,
                agent_en=agent_id,
                summary=summary,
                event=ActivityEvent.LLM,
            ),
            workflow=workflow,
        )

    if event_type in {"tool.call", "tool.result", "tool.error"}:
        tool = (payload.get("data") or {}).get("tool") or {}
        tool_name = str(tool.get("name") or "")
        if not tool_name:
            return None
        if event_type == "tool.call":
            tool_status = ActivityStatus.RUNNING
        elif event_type == "tool.result":
            tool_status = ActivityStatus.DONE
        else:
            tool_status = ActivityStatus.ERROR
        return _build_stream_event(
            run_id=state.run_id,
            status=RunStatus.RUNNING,
            activity=_build_activity(
                status=tool_status,
                agent_cn=agent_name,
                agent_en=agent_id,
                summary="",
                event=ActivityEvent.TOOL,
                event_name=tool_name,
            ),
            workflow=workflow,
        )

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

        if mapped.get("status") in {"error", "aborted"}:
            if on_run_complete and not for_complete_called:
                on_run_complete()
                for_complete_called = True
            yield {"id": str(event_id or state.last_event_id), "data": json.dumps(mapped, ensure_ascii=False)}
            return

        yield {"id": str(event_id or state.last_event_id), "data": json.dumps(mapped, ensure_ascii=False)}

    reply = _build_done_event(state)
    if not reply:
        return

    final_id = state.last_event_id + 1 if state.last_event_id else 1
    if on_run_complete and not for_complete_called:
        on_run_complete()
    yield {"id": str(final_id), "data": json.dumps(reply, ensure_ascii=False)}
