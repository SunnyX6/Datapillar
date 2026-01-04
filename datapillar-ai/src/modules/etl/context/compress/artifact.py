"""
智能体产出压缩：索引层（按 session 隔离）

重要原则（与你确认一致）：
- 不“压缩/总结 SQL 内容”来替代原文；只做索引（版本/状态/hash/长度/关联证据）
- 原件（完整 SQL/完整 plan/test JSON）仍应由上层存入 session state/checkpoint
- 本模块不与 Orchestrator 集成，只提供数据结构与确定性更新函数
"""

from __future__ import annotations

import hashlib
import json
import time
from enum import Enum
from typing import Any, Literal, cast

from pydantic import BaseModel, Field, field_validator

AgentId = Literal[
    "knowledge_agent",
    "analyst_agent",
    "architect_agent",
    "developer_agent",
    "tester_agent",
    "commander",
]


def _now_ms() -> int:
    return int(time.time() * 1000)


def _sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _fingerprint_json(value: Any) -> str:
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, default=str)
    return _sha256_text(raw)


class ArtifactType(str, Enum):
    analysis = "analysis"
    plan = "plan"
    test = "test"
    sql_workflow = "sql_workflow"
    sql_job = "sql_job"


class ArtifactStatus(str, Enum):
    active = "active"
    superseded = "superseded"
    invalid = "invalid"


class EvidenceRecord(BaseModel):
    """
    证据目录条目（工具证据）

    说明：
    - output_preview 是“工具输出的短预览”，不是 LLM 总结
    - input/output_fingerprint 用于去重与审计
    """

    session_id: str
    evidence_id: str
    tool_name: str
    collected_by: AgentId | None = None
    input_fingerprint: str
    output_fingerprint: str
    output_preview: str = Field(default="")
    created_at_ms: int = Field(default_factory=_now_ms)


class EvidenceIndex(BaseModel):
    records: dict[str, EvidenceRecord] = Field(default_factory=dict)


def register_tool_evidence(
    *,
    index: EvidenceIndex | None,
    session_id: str,
    tool_name: str,
    collected_by: AgentId | None = None,
    tool_input: Any,
    tool_output: Any,
    max_preview_chars: int = 300,
) -> tuple[EvidenceIndex, str]:
    """
    登记工具证据（确定性）

    返回：
    - new_index：更新后的证据目录
    - evidence_id：证据 ID（由指纹确定性生成）
    """

    input_fp = _fingerprint_json(tool_input)
    output_fp = _fingerprint_json(tool_output)
    evidence_id = (
        f"ev_{_sha256_text(session_id + ':' + tool_name + ':' + input_fp + ':' + output_fp)[:16]}"
    )

    preview = ""
    if max_preview_chars > 0:
        text = str(tool_output)
        preview = text if len(text) <= max_preview_chars else text[:max_preview_chars]

    base = index or EvidenceIndex()
    if evidence_id in base.records:
        return base, evidence_id

    rec = EvidenceRecord(
        session_id=session_id,
        evidence_id=evidence_id,
        tool_name=tool_name,
        collected_by=collected_by,
        input_fingerprint=input_fp,
        output_fingerprint=output_fp,
        output_preview=preview,
    )
    records = dict(base.records)
    records[evidence_id] = rec
    return base.model_copy(update={"records": records}), evidence_id


class ArtifactRecord(BaseModel):
    """
    产物索引条目（目录）

    约束：
    - 不包含 SQL 原文（只保留 hash/长度等索引信息）
    - summary 允许是“短句标签”，但不能替代原文事实
    """

    session_id: str
    artifact_id: str
    artifact_type: ArtifactType
    produced_by: AgentId
    status: ArtifactStatus = Field(default=ArtifactStatus.active)
    created_at_ms: int = Field(default_factory=_now_ms)

    requirement_revision: int = Field(default=0, ge=0)
    summary: str = Field(default="")
    key_fields: dict[str, Any] = Field(default_factory=dict)

    evidence_ids: list[str] = Field(default_factory=list)
    parent_artifact_ids: list[str] = Field(default_factory=list)

    job_id: str | None = None
    sql_hash: str | None = None
    sql_length_chars: int | None = None
    payload_fingerprint: str | None = None
    payload_length_chars: int | None = None

    @field_validator("artifact_id", mode="before")
    @classmethod
    def _strip_artifact_id(cls, v: Any) -> Any:
        return v.strip() if isinstance(v, str) else v


class ArtifactIndex(BaseModel):
    records: dict[str, ArtifactRecord] = Field(default_factory=dict)


def _assert_index_session(*, base: ArtifactIndex, session_id: str) -> None:
    if not isinstance(session_id, str) or not session_id.strip():
        raise ValueError("session_id 不能为空")
    bad = [
        rec.session_id
        for rec in base.records.values()
        if isinstance(rec.session_id, str) and rec.session_id and rec.session_id != session_id
    ]
    if bad:
        raise ValueError(f"ArtifactIndex 中存在不同 session_id 的记录: {sorted(set(bad))}")


def _new_artifact_id(*, artifact_type: ArtifactType, parts: list[str]) -> str:
    fingerprint = _sha256_text("|".join(parts))
    return f"af_{artifact_type.value}_{fingerprint[:16]}"


def _merge_unique_strs(base: list[str], incoming: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for item in list(base) + list(incoming):
        if not isinstance(item, str):
            continue
        s = item.strip()
        if not s or s in seen:
            continue
        seen.add(s)
        out.append(s)
    return out


def _upsert_record(*, base: ArtifactIndex, rec: ArtifactRecord) -> ArtifactIndex:
    records = dict(base.records)
    existing = records.get(rec.artifact_id)
    if not existing:
        records[rec.artifact_id] = rec
        return base.model_copy(update={"records": records})

    merged = existing.model_copy(
        update={
            "status": rec.status,
            "summary": rec.summary or existing.summary,
            "key_fields": rec.key_fields or existing.key_fields,
            "evidence_ids": _merge_unique_strs(existing.evidence_ids, rec.evidence_ids),
            "parent_artifact_ids": _merge_unique_strs(
                existing.parent_artifact_ids, rec.parent_artifact_ids
            ),
            "requirement_revision": max(existing.requirement_revision, rec.requirement_revision),
            "job_id": rec.job_id or existing.job_id,
            "sql_hash": rec.sql_hash or existing.sql_hash,
            "sql_length_chars": rec.sql_length_chars or existing.sql_length_chars,
            "payload_fingerprint": rec.payload_fingerprint or existing.payload_fingerprint,
            "payload_length_chars": rec.payload_length_chars or existing.payload_length_chars,
        }
    )
    records[rec.artifact_id] = merged
    return base.model_copy(update={"records": records})


def _supersede_matching(
    *,
    base: ArtifactIndex,
    predicate,
) -> ArtifactIndex:
    records = dict(base.records)
    changed = False
    for aid, rec in list(records.items()):
        if rec.status != ArtifactStatus.active:
            continue
        if not predicate(rec):
            continue
        records[aid] = rec.model_copy(update={"status": ArtifactStatus.superseded})
        changed = True
    return base.model_copy(update={"records": records}) if changed else base


def register_job(
    *,
    index: ArtifactIndex | None,
    session_id: str,
    requirement_revision: int,
    job_id: str,
    sql_text: str,
    produced_by: AgentId = "developer_agent",
    evidence_ids: list[str] | None = None,
    parent_artifact_ids: list[str] | None = None,
) -> tuple[ArtifactIndex, str]:
    """
    登记 Job 级 SQL 产物索引（不保存 SQL 原文）
    """

    sql_hash = _sha256_text(sql_text)
    sql_len = len(sql_text)
    artifact_id = _new_artifact_id(
        artifact_type=ArtifactType.sql_job,
        parts=[session_id, str(requirement_revision), "job", job_id, sql_hash],
    )

    base = index or ArtifactIndex()
    _assert_index_session(base=base, session_id=session_id)
    base = _supersede_matching(
        base=base,
        predicate=lambda r: r.session_id == session_id
        and r.artifact_type == ArtifactType.sql_job
        and r.job_id == job_id,
    )

    rec = ArtifactRecord(
        session_id=session_id,
        artifact_id=artifact_id,
        artifact_type=ArtifactType.sql_job,
        produced_by=produced_by,
        status=ArtifactStatus.active,
        requirement_revision=requirement_revision,
        summary=f"SQL(Job={job_id})",
        key_fields={"job_id": job_id},
        job_id=job_id,
        sql_hash=sql_hash,
        sql_length_chars=sql_len,
        evidence_ids=list(evidence_ids or []),
        parent_artifact_ids=list(parent_artifact_ids or []),
    )

    return _upsert_record(base=base, rec=rec), artifact_id


def register_workflow(
    *,
    index: ArtifactIndex | None,
    session_id: str,
    requirement_revision: int,
    sql_by_job: dict[str, str],
    produced_by: AgentId = "developer_agent",
    evidence_ids: list[str] | None = None,
    parent_artifact_ids: list[str] | None = None,
) -> tuple[ArtifactIndex, str]:
    """
    登记 workflow 级 SQL 产物索引（整次生成的 SQL bundle）

    说明：
    - 不保存 SQL 原文，只保存“按 job 的 sql_hash/长度”与总指纹
    - 会将上一份 active 的 sql_workflow 产物标记为 superseded
    """

    job_items: list[tuple[str, str, int]] = []
    for job_id in sorted(sql_by_job.keys()):
        sql_text = sql_by_job[job_id] or ""
        job_items.append((job_id, _sha256_text(sql_text), len(sql_text)))

    fingerprint = _fingerprint_json(job_items)
    artifact_id = _new_artifact_id(
        artifact_type=ArtifactType.sql_workflow,
        parts=[session_id, str(requirement_revision), "workflow", fingerprint],
    )

    base = index or ArtifactIndex()
    _assert_index_session(base=base, session_id=session_id)
    base = _supersede_matching(
        base=base,
        predicate=lambda r: r.session_id == session_id
        and r.artifact_type == ArtifactType.sql_workflow,
    )

    key_fields = {
        "job_count": len(job_items),
        "jobs": [{"job_id": j, "sql_hash": h, "sql_length_chars": ln} for j, h, ln in job_items],
    }
    rec = ArtifactRecord(
        session_id=session_id,
        artifact_id=artifact_id,
        artifact_type=ArtifactType.sql_workflow,
        produced_by=produced_by,
        status=ArtifactStatus.active,
        requirement_revision=requirement_revision,
        summary=f"SQL(Workflow, jobs={len(job_items)})",
        key_fields=key_fields,
        evidence_ids=list(evidence_ids or []),
        parent_artifact_ids=list(parent_artifact_ids or []),
        sql_hash=fingerprint,
        sql_length_chars=sum(ln for _, _, ln in job_items),
    )

    return _upsert_record(base=base, rec=rec), artifact_id


def register_structured_artifact(
    *,
    index: ArtifactIndex | None,
    session_id: str,
    requirement_revision: int,
    artifact_type: ArtifactType,
    payload: Any,
    produced_by: AgentId,
    summary: str,
    key_fields: dict[str, Any] | None = None,
    evidence_ids: list[str] | None = None,
    parent_artifact_ids: list[str] | None = None,
) -> tuple[ArtifactIndex, str]:
    """
    登记结构化产物索引（analysis/plan/test 等）

    约束：
    - 不保存 payload 原文，只保存指纹与长度
    - 默认同类型只保留最新 active（会 supersede 上一份）
    """

    payload_fp = _fingerprint_json(payload)
    payload_len = len(json.dumps(payload, ensure_ascii=False, sort_keys=True, default=str))
    artifact_id = _new_artifact_id(
        artifact_type=artifact_type,
        parts=[session_id, str(requirement_revision), "payload", payload_fp],
    )

    base = index or ArtifactIndex()
    _assert_index_session(base=base, session_id=session_id)
    base = _supersede_matching(
        base=base,
        predicate=lambda r: r.session_id == session_id and r.artifact_type == artifact_type,
    )

    rec = ArtifactRecord(
        session_id=session_id,
        artifact_id=artifact_id,
        artifact_type=artifact_type,
        produced_by=produced_by,
        status=ArtifactStatus.active,
        requirement_revision=requirement_revision,
        summary=summary,
        key_fields=dict(key_fields or {}),
        evidence_ids=list(evidence_ids or []),
        parent_artifact_ids=list(parent_artifact_ids or []),
        payload_fingerprint=payload_fp,
        payload_length_chars=payload_len,
    )
    return _upsert_record(base=base, rec=rec), artifact_id


class ArtifactStore(BaseModel):
    """
    会话级产物索引存储（显式带 session_id）

    - global_index：同 session 的全局目录（跨 agent）
    - agent_indexes：同 session 的每 agent 目录（便于“谁说的”）
    - evidence_index：工具证据目录（同 session）
    """

    session_id: str
    global_index: ArtifactIndex = Field(default_factory=ArtifactIndex)
    agent_indexes: dict[AgentId, ArtifactIndex] = Field(default_factory=dict)
    evidence_index: EvidenceIndex = Field(default_factory=EvidenceIndex)


def _get_agent_index(*, store: ArtifactStore, agent_id: AgentId) -> ArtifactIndex:
    return store.agent_indexes.get(agent_id) or ArtifactIndex()


def register_evidence(
    *,
    store: ArtifactStore,
    tool_name: str,
    collected_by: AgentId | None,
    tool_input: Any,
    tool_output: Any,
    max_preview_chars: int = 300,
) -> tuple[ArtifactStore, str]:
    idx, evidence_id = register_tool_evidence(
        index=store.evidence_index,
        session_id=store.session_id,
        tool_name=tool_name,
        collected_by=collected_by,
        tool_input=tool_input,
        tool_output=tool_output,
        max_preview_chars=max_preview_chars,
    )
    return store.model_copy(update={"evidence_index": idx}), evidence_id


def register_artifact(
    *,
    store: ArtifactStore,
    agent_id: AgentId,
    artifact_type: ArtifactType,
    requirement_revision: int,
    registration: ArtifactRegistration,
) -> tuple[ArtifactStore, str]:
    """
    统一入口：把产物同时写入 global_index 与 agent_index（同 session）
    """

    summary = registration.summary
    payload = registration.payload
    sql_text = registration.sql_text
    job_id = registration.job_id
    key_fields = registration.key_fields
    evidence_ids = registration.evidence_ids
    parent_artifact_ids = registration.parent_artifact_ids

    if artifact_type == ArtifactType.sql_job:
        if job_id is None or sql_text is None:
            raise ValueError("sql_job 需要 job_id 与 sql_text")
        global_idx, artifact_id = register_job(
            index=store.global_index,
            session_id=store.session_id,
            requirement_revision=requirement_revision,
            job_id=job_id,
            sql_text=sql_text,
            produced_by=agent_id,
            evidence_ids=evidence_ids,
            parent_artifact_ids=parent_artifact_ids,
        )
        agent_idx, _ = register_job(
            index=_get_agent_index(store=store, agent_id=agent_id),
            session_id=store.session_id,
            requirement_revision=requirement_revision,
            job_id=job_id,
            sql_text=sql_text,
            produced_by=agent_id,
            evidence_ids=evidence_ids,
            parent_artifact_ids=parent_artifact_ids,
        )
    elif artifact_type == ArtifactType.sql_workflow:
        if not isinstance(payload, dict):
            raise ValueError("sql_workflow 需要 payload=sql_by_job dict[str,str]")
        global_idx, artifact_id = register_workflow(
            index=store.global_index,
            session_id=store.session_id,
            requirement_revision=requirement_revision,
            sql_by_job=cast(dict[str, str], payload),
            produced_by=agent_id,
            evidence_ids=evidence_ids,
            parent_artifact_ids=parent_artifact_ids,
        )
        agent_idx, _ = register_workflow(
            index=_get_agent_index(store=store, agent_id=agent_id),
            session_id=store.session_id,
            requirement_revision=requirement_revision,
            sql_by_job=cast(dict[str, str], payload),
            produced_by=agent_id,
            evidence_ids=evidence_ids,
            parent_artifact_ids=parent_artifact_ids,
        )
    else:
        if payload is None:
            raise ValueError(f"{artifact_type.value} 需要 payload")
        global_idx, artifact_id = register_structured_artifact(
            index=store.global_index,
            session_id=store.session_id,
            requirement_revision=requirement_revision,
            artifact_type=artifact_type,
            payload=payload,
            produced_by=agent_id,
            summary=summary,
            key_fields=key_fields,
            evidence_ids=evidence_ids,
            parent_artifact_ids=parent_artifact_ids,
        )
        agent_idx, _ = register_structured_artifact(
            index=_get_agent_index(store=store, agent_id=agent_id),
            session_id=store.session_id,
            requirement_revision=requirement_revision,
            artifact_type=artifact_type,
            payload=payload,
            produced_by=agent_id,
            summary=summary,
            key_fields=key_fields,
            evidence_ids=evidence_ids,
            parent_artifact_ids=parent_artifact_ids,
        )

    agent_indexes = dict(store.agent_indexes)
    agent_indexes[agent_id] = agent_idx
    return (
        store.model_copy(update={"global_index": global_idx, "agent_indexes": agent_indexes}),
        artifact_id,
    )


class ArtifactRegistration(BaseModel):
    """
    产物登记参数（为了降低函数参数数量）
    """

    summary: str
    payload: Any | None = None
    sql_text: str | None = None
    job_id: str | None = None
    key_fields: dict[str, Any] | None = None
    evidence_ids: list[str] | None = None
    parent_artifact_ids: list[str] | None = None


def _latest_active(*, index: ArtifactIndex, artifact_type: ArtifactType) -> ArtifactRecord | None:
    candidates = [
        rec
        for rec in index.records.values()
        if rec.artifact_type == artifact_type and rec.status == ArtifactStatus.active
    ]
    if not candidates:
        return None
    candidates.sort(key=lambda r: r.created_at_ms, reverse=True)
    return candidates[0]


def _records_by_ids(*, index: ArtifactIndex, artifact_ids: list[str]) -> list[ArtifactRecord]:
    out: list[ArtifactRecord] = []
    for aid in artifact_ids:
        rec = index.records.get(aid)
        if rec:
            out.append(rec)
    return out


def _select_focus(
    *,
    index: ArtifactIndex,
    session_id: str,
    job_ids: list[str],
    max_count: int,
) -> list[ArtifactRecord]:
    if not job_ids or max_count <= 0:
        return []
    wanted = {j.strip() for j in job_ids if isinstance(j, str) and j.strip()}
    if not wanted:
        return []
    candidates = [
        rec
        for rec in index.records.values()
        if rec.status == ArtifactStatus.active
        and rec.session_id == session_id
        and rec.job_id
        and rec.job_id in wanted
    ]
    candidates.sort(key=lambda r: r.created_at_ms, reverse=True)
    return candidates[:max_count]


def _render_evidence_previews(
    *, store: ArtifactStore, evidence_ids: list[str], max_count: int
) -> list[dict[str, Any]]:
    if max_count <= 0:
        return []
    out: list[dict[str, Any]] = []
    for ev_id in evidence_ids[-max_count:]:
        rec = store.evidence_index.records.get(ev_id)
        if not rec:
            continue
        out.append(
            {
                "evidence_id": rec.evidence_id,
                "tool_name": rec.tool_name,
                "collected_by": rec.collected_by,
                "output_preview": rec.output_preview,
            }
        )
    return out


def build_context_view(
    *,
    store: ArtifactStore,
    agent_id: AgentId,
    focus_job_ids: list[str] | None = None,
    max_recent_per_agent: int = 3,
    max_focus: int = 5,
    max_evidence_per_artifact: int = 3,
) -> dict[str, Any]:
    """
    构造“产物压缩投喂视图”（三段式：Global 必要锚点 + Focus + Agent 连贯性）
    """

    anchors: list[ArtifactRecord] = []
    for t in ArtifactType:
        rec = _latest_active(index=store.global_index, artifact_type=t)
        if rec and rec.session_id == store.session_id:
            anchors.append(rec)

    focus = _select_focus(
        index=store.global_index,
        session_id=store.session_id,
        job_ids=list(focus_job_ids or []),
        max_count=max_focus,
    )
    focus_parent_ids = _merge_unique_strs([], [pid for r in focus for pid in r.parent_artifact_ids])
    focus_parents = _records_by_ids(index=store.global_index, artifact_ids=focus_parent_ids)

    agent_index = _get_agent_index(store=store, agent_id=agent_id)
    recent_candidates = [
        rec
        for rec in agent_index.records.values()
        if rec.status == ArtifactStatus.active
        and rec.session_id == store.session_id
        and rec.produced_by == agent_id
    ]
    recent_candidates.sort(key=lambda r: r.created_at_ms, reverse=True)
    recent = recent_candidates[: max(0, int(max_recent_per_agent))]

    ordered: list[ArtifactRecord] = []
    for part in (anchors, focus, focus_parents, recent):
        ordered.extend(part)

    seen: set[str] = set()
    items: list[dict[str, Any]] = []
    for rec in ordered:
        if rec.artifact_id in seen:
            continue
        seen.add(rec.artifact_id)
        items.append(
            {
                "artifact_id": rec.artifact_id,
                "artifact_type": rec.artifact_type.value,
                "status": rec.status.value,
                "produced_by": rec.produced_by,
                "requirement_revision": rec.requirement_revision,
                "summary": rec.summary,
                "key_fields": rec.key_fields,
                "job_id": rec.job_id,
                "sql_hash": rec.sql_hash,
                "sql_length_chars": rec.sql_length_chars,
                "payload_fingerprint": rec.payload_fingerprint,
                "payload_length_chars": rec.payload_length_chars,
                "created_at_ms": rec.created_at_ms,
                "evidence_previews": _render_evidence_previews(
                    store=store,
                    evidence_ids=rec.evidence_ids,
                    max_count=max(0, int(max_evidence_per_artifact)),
                ),
                "parent_artifact_ids": list(rec.parent_artifact_ids),
            }
        )

    return {
        "session_id": store.session_id,
        "agent_id": agent_id,
        "counts": {
            "anchors": len(anchors),
            "focus": len(focus),
            "recent": len(recent),
            "total_items": len(items),
        },
        "items": items,
    }


def find_job(
    *,
    index: ArtifactIndex | None,
    session_id: str,
    job_id: str,
) -> ArtifactRecord | None:
    """
    查询指定 job_id 的当前 active SQL artifact（目录）
    """

    base = index or ArtifactIndex()
    _assert_index_session(base=base, session_id=session_id)
    candidates = [
        rec
        for rec in base.records.values()
        if rec.artifact_type == ArtifactType.sql_job
        and rec.session_id == session_id
        and rec.job_id == job_id
        and rec.status == ArtifactStatus.active
    ]
    if not candidates:
        return None
    candidates.sort(key=lambda r: r.created_at_ms, reverse=True)
    return candidates[0]


def invalidate_before(
    *,
    index: ArtifactIndex | None,
    session_id: str,
    current_requirement_revision: int,
) -> ArtifactIndex:
    """
    将旧 revision 的产物标记为 invalid（不删除）
    """

    base = index or ArtifactIndex()
    _assert_index_session(base=base, session_id=session_id)
    records = dict(base.records)
    changed = False
    for aid, rec in list(records.items()):
        if rec.session_id != session_id:
            continue
        if rec.requirement_revision >= current_requirement_revision:
            continue
        if rec.status == ArtifactStatus.invalid:
            continue
        records[aid] = rec.model_copy(update={"status": ArtifactStatus.invalid})
        changed = True
    return base.model_copy(update={"records": records}) if changed else base
