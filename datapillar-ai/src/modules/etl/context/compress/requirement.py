"""
用户需求对话压缩（Requirement TODO）

目标：
- 只处理“用户需求对话”的压缩，不掺入任何智能体产出（避免污染事实源）
- 充分利用 LLM 的总结/抽取能力，但输出必须结构化、可合并、可回滚
- 不在此处做多智能体集成（由编排层决定何时注入给哪个 Agent）

核心产物：
- RequirementTodoSnapshot：当前会话的“结构化 TODO 清单”（短、稳定）
- RequirementTodoDelta：每次用户新输入产生的增量变更（add/update/cancel/reset）

注意：
- 本模块不负责“是否正确”，只负责把用户表述收敛成可执行/可追问的 TODO。
- 为了避免漂移，推荐只用用户消息作为输入；智能体产出在后续“产出压缩”阶段再讨论。
"""

from __future__ import annotations

import hashlib
import json
from enum import Enum
from typing import Any, Literal

from langchain_core.messages import BaseMessage, SystemMessage
from pydantic import BaseModel, Field, field_validator, model_validator

from src.shared.config.settings import settings


def _sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _derive_turn_index(*, current_snapshot: RequirementTodoSnapshot) -> int:
    """
    生成本轮用户输入的 turn_index（会话内递增轮次）

    注意：
    - 这是“需求压缩模块内部的会话轮次”，不依赖外部消息系统
    - 约定：每处理一次用户输入并 apply_delta，snapshot.revision +1
    """

    return int(current_snapshot.revision) + 1


def _derive_message_id(*, session_id: str, turn_index: int, message_text: str) -> str:
    """
    生成 message_id（确定性，需求压缩模块内部的“用户输入事件ID”）

    说明：
    - 不依赖数据库/外部消息系统；该 ID 由本模块生成并写入 evidence_history
    - 设计目标：同 session 内稳定、可追溯、可去重
    """

    if not isinstance(session_id, str) or not session_id.strip():
        raise ValueError("session_id 不能为空")
    if not isinstance(message_text, str) or not message_text.strip():
        raise ValueError("message_text 不能为空")
    raw = f"{session_id}:{int(turn_index)}:{message_text}"
    return f"msg_{_sha256_text(raw)[:16]}"


def derive_turn_index(*, current_snapshot: RequirementTodoSnapshot) -> int:
    """
    对外公开：生成本轮 turn_index（会话内递增轮次）。

    注意：
    - turn_index 是“需求压缩模块内部的会话轮次”，用于 evidence 锚点追溯与去重
    """

    return _derive_turn_index(current_snapshot=current_snapshot)


def derive_message_id(*, session_id: str, turn_index: int, message_text: str) -> str:
    """
    对外公开：生成 message_id（需求压缩模块内部的用户输入事件ID）。

    约束：
    - 不依赖数据库/外部消息系统
    - 同 session 内确定性可复现
    """

    return _derive_message_id(
        session_id=session_id, turn_index=turn_index, message_text=message_text
    )


def get_evidence_limit() -> int:
    """
    投喂视图：每条 TODO 保留最近多少条“变更锚点”（不含起源锚点）。

    注意：
    - 这是“喂给 LLM 的视图上限”，不是审计历史上限
    - 审计历史（evidence_history）允许无限增长
    """

    try:
        raw = settings.get("etl_requirement_todo_evidence_recent_k", 3)
        return max(0, min(int(raw), 20))
    except Exception:
        return 3


def get_keep_origin() -> bool:
    """
    投喂视图：是否强制保留“起源锚点”（第一条 origin evidence）。
    """

    try:
        return bool(settings.get("etl_requirement_todo_evidence_keep_origin", True))
    except Exception:
        return True


class TodoType(str, Enum):
    goal = "goal"
    task = "task"
    constraint = "constraint"
    question = "question"
    definition = "definition"
    acceptance = "acceptance"


class TodoStatus(str, Enum):
    open = "open"
    done = "done"
    canceled = "canceled"


class TodoPriority(str, Enum):
    p0 = "p0"
    p1 = "p1"
    p2 = "p2"


class TodoOwner(str, Enum):
    user = "user"
    analyst = "analyst"
    architect = "architect"
    developer = "developer"
    tester = "tester"
    system = "system"


class EvidenceKind(str, Enum):
    origin = "origin"
    update = "update"
    cancel = "cancel"


class EvidenceAnchor(BaseModel):
    """
    证据锚点（可追溯）

    设计要点：
    - quote 是“短摘录”，用于帮助定位原文，不承担完整存档职责
    - quote_hash 用于去重与审计（确定性）
    - turn_index/message_id 作为可选指针（上层有能力时可回填）
    """

    kind: EvidenceKind
    quote: str
    quote_hash: str = Field(default="")
    turn_index: int | None = None
    message_id: str | None = None

    @field_validator("quote", mode="before")
    @classmethod
    def _strip_quote(cls, v: Any) -> Any:
        return v.strip() if isinstance(v, str) else v

    @model_validator(mode="after")
    def _fill_quote_hash(self) -> EvidenceAnchor:
        if not self.quote_hash:
            self.quote_hash = _sha256_text(f"{self.kind.value}:{self.quote}")
        return self


def _merge_evidence_history(
    base: list[EvidenceAnchor], incoming: list[EvidenceAnchor]
) -> list[EvidenceAnchor]:
    """
    合并证据锚点（保序去重，确定性）
    """

    seen: set[str] = set()
    out: list[EvidenceAnchor] = []
    for item in list(base) + list(incoming):
        key = item.quote_hash or _sha256_text(f"{item.kind.value}:{item.quote}")
        if key in seen:
            continue
        seen.add(key)
        out.append(item)
    return out


def _evidence_view(
    *,
    history: list[EvidenceAnchor],
    keep_origin: bool,
    recent_k: int,
) -> list[str]:
    """
    从全量 history 生成“投喂视图”（受控长度，不丢审计历史）

    策略：
    - 如 keep_origin=True：保留第一条 origin
    - 再追加最近 recent_k 条非 origin 锚点（按时间顺序）
    """

    if not history:
        return []

    origin: EvidenceAnchor | None = None
    if keep_origin:
        for h in history:
            if h.kind == EvidenceKind.origin and h.quote:
                origin = h
                break

    non_origin = [h for h in history if h.kind != EvidenceKind.origin and h.quote]
    selected = non_origin[-recent_k:] if recent_k > 0 else []

    selected_history: list[EvidenceAnchor] = []
    if origin is not None:
        selected_history.append(origin)
    selected_history.extend(selected)

    seen_quotes: set[str] = set()
    out: list[str] = []
    for h in selected_history:
        q = h.quote.strip() if isinstance(h.quote, str) else ""
        if not q:
            continue
        if q in seen_quotes:
            continue
        seen_quotes.add(q)
        out.append(q)
    return out


def _normalize_item_history(
    *,
    item: RequirementTodoItem,
    keep_origin: bool,
    recent_k: int,
) -> RequirementTodoItem:
    """
    兼容旧数据：当 evidence_history 为空但 evidence 有值时，将 evidence 视为 origin 锚点导入 history。
    """

    base_history = list(item.evidence_history or [])
    if not base_history and item.evidence:
        base_history = [EvidenceAnchor(kind=EvidenceKind.origin, quote=q) for q in item.evidence]

    view = _evidence_view(history=base_history, keep_origin=keep_origin, recent_k=recent_k)
    return item.model_copy(update={"evidence_history": base_history, "evidence": view})


def build_snapshot_view(
    *,
    snapshot: RequirementTodoSnapshot | None,
    keep_origin: bool,
    recent_k: int,
) -> dict[str, Any]:
    """
    构造“投喂视图快照”

    说明：
    - 只包含结构化字段 + evidence 投喂视图
    - 不包含 evidence_history（审计历史），避免无限膨胀导致上下文爆炸
    """

    if snapshot is None:
        raise ValueError("current_snapshot 不能为空：RequirementTodoSnapshot 必须包含 session_id")
    base = snapshot
    items_view: list[dict[str, Any]] = []
    for item in base.items:
        normalized = _normalize_item_history(item=item, keep_origin=keep_origin, recent_k=recent_k)
        items_view.append(
            {
                "id": normalized.id,
                "type": normalized.type.value,
                "title": normalized.title,
                "status": normalized.status.value,
                "priority": normalized.priority.value,
                "owner": normalized.owner.value,
                "reason": normalized.reason,
                "evidence": list(normalized.evidence or []),
            }
        )
    return {"session_id": base.session_id, "revision": base.revision, "items": items_view}


class RequirementTodoItem(BaseModel):
    """
    TODO 条目（结构化）

    约束：
    - title 必须短句（建议 <= 80 字）
    - evidence 用于追溯：建议放“来自用户原话的片段”，而不是智能体解释
    """

    id: str = Field(..., description="稳定 ID（用于后续更新/作废，不靠文本匹配）")
    type: TodoType = Field(..., description="TODO 类型")
    title: str = Field(..., description="一句话描述（短句、可执行）")
    status: TodoStatus = Field(default=TodoStatus.open, description="open/done/canceled")
    priority: TodoPriority = Field(default=TodoPriority.p1, description="p0/p1/p2")
    owner: TodoOwner = Field(default=TodoOwner.system, description="归属：user/analyst/.../system")

    reason: str | None = Field(default=None, description="取消/改动原因（可选）")
    evidence: list[str] = Field(
        default_factory=list,
        description="证据投喂视图：短摘录（受控上限），用于给 LLM/人快速理解与定位",
    )
    evidence_history: list[EvidenceAnchor] = Field(
        default_factory=list,
        description="证据审计历史（全量，不直接投喂给 LLM）",
    )

    @field_validator("id", mode="before")
    @classmethod
    def _strip_id(cls, v: Any) -> Any:
        return v.strip() if isinstance(v, str) else v

    @field_validator("title", mode="before")
    @classmethod
    def _strip_title(cls, v: Any) -> Any:
        return v.strip() if isinstance(v, str) else v

    @field_validator("evidence", mode="before")
    @classmethod
    def _normalize_evidence(cls, v: Any) -> Any:
        if v is None:
            return []
        if isinstance(v, list):
            out: list[str] = []
            for item in v:
                if isinstance(item, str) and item.strip():
                    out.append(item.strip())
            return out
        return v


class RequirementTodoSnapshot(BaseModel):
    """
    TODO 快照（当前会话的“压缩结果”）

    revision：每次 apply_delta 都会 +1，用于后续集成时做一致性控制。
    """

    session_id: str = Field(..., description="会话ID（强制）")
    revision: int = Field(default=0, ge=0)
    items: list[RequirementTodoItem] = Field(default_factory=list)


def fingerprint_requirement_snapshot(snapshot: RequirementTodoSnapshot | None) -> str | None:
    """
    计算“需求指纹”（确定性）

    目的：
    - 用于判断历史产物（analysis/plan/test/SQL）是否仍对齐当前需求
    - 必须排除 evidence（用户原话锚点会不断追加，不能导致需求被误判为变化）
    """

    if snapshot is None:
        return None

    items: list[dict[str, Any]] = []
    for it in list(snapshot.items or []):
        payload = it.model_dump() if hasattr(it, "model_dump") else dict(it)  # type: ignore[arg-type]
        if not isinstance(payload, dict):
            continue
        payload.pop("evidence", None)
        items.append(payload)

    items.sort(key=lambda x: str(x.get("id") or ""))
    stable = {"session_id": snapshot.session_id, "items": items}
    raw = json.dumps(stable, ensure_ascii=False, sort_keys=True, default=str)
    return _sha256_text(raw)


class TodoUpdate(BaseModel):
    """按 ID 更新 TODO 条目（Patch 语义）"""

    id: str
    title: str | None = None
    status: TodoStatus | None = None
    priority: TodoPriority | None = None
    owner: TodoOwner | None = None
    reason: str | None = None
    evidence_append: list[str] = Field(default_factory=list)

    @field_validator("id", mode="before")
    @classmethod
    def _strip_id(cls, v: Any) -> Any:
        return v.strip() if isinstance(v, str) else v

    @field_validator("title", mode="before")
    @classmethod
    def _strip_title(cls, v: Any) -> Any:
        return v.strip() if isinstance(v, str) else v

    @field_validator("evidence_append", mode="before")
    @classmethod
    def _normalize_evidence_append(cls, v: Any) -> Any:
        if v is None:
            return []
        if isinstance(v, list):
            out: list[str] = []
            for item in v:
                if isinstance(item, str) and item.strip():
                    out.append(item.strip())
            return out
        return v


class TodoCancel(BaseModel):
    """按 ID 作废 TODO 条目"""

    id: str
    reason: str | None = None
    evidence_append: list[str] = Field(default_factory=list)

    @field_validator("id", mode="before")
    @classmethod
    def _strip_id(cls, v: Any) -> Any:
        return v.strip() if isinstance(v, str) else v

    @field_validator("reason", mode="before")
    @classmethod
    def _strip_reason(cls, v: Any) -> Any:
        return v.strip() if isinstance(v, str) else v

    @field_validator("evidence_append", mode="before")
    @classmethod
    def _normalize_evidence_append(cls, v: Any) -> Any:
        if v is None:
            return []
        if isinstance(v, list):
            out: list[str] = []
            for item in v:
                if isinstance(item, str) and item.strip():
                    out.append(item.strip())
            return out
        return v


class RequirementTodoDelta(BaseModel):
    """
    每次用户新输入的 TODO 变更（LLM 输出）

    reset=True：
    - 表示用户明确表达“从头来/忽略之前/重做/重置”
    - apply_delta 将清空现有 items，仅保留 add 的新条目（revision 仍会 +1）
    """

    reset: bool = Field(default=False)
    add: list[RequirementTodoItem] = Field(default_factory=list)
    update: list[TodoUpdate] = Field(default_factory=list)
    cancel: list[TodoCancel] = Field(default_factory=list)


_REQUIREMENT_TODO_DELTA_SYSTEM_PROMPT = """你是 Datapillar ETL 的“用户需求压缩器”。

你的输入只包含用户对话（Human 消息）。你的任务是把“用户最新输入”合并进已有的 TODO 快照中，输出一个“增量变更（delta）”。

要求：
1) 只输出纯 JSON（禁止 Markdown、禁止代码块、禁止解释性文字）
2) delta 必须短、可合并：优先用 update/cancel 修改旧条目，而不是重复 add
3) 必须处理隐式否定：用户说“垃圾/不行/不好/换个口径/改一下/别按之前做”，通常意味着 cancel 或 update
4) evidence 必须引用用户原话片段（短句即可），不要写你的推理
5) 用户明确要求“从头来/忽略之前/重做/重置”时，reset=true

delta JSON 结构：
{
  "reset": false,
  "add": [
    {
      "id": "t1",
      "type": "goal|task|constraint|question|definition|acceptance",
      "title": "一句话短句",
      "status": "open|done|canceled",
      "priority": "p0|p1|p2",
      "owner": "user|analyst|architect|developer|tester|system",
      "reason": null,
      "evidence": ["用户原话片段"]
    }
  ],
  "update": [
    {
      "id": "t1",
      "title": "新标题(可选)",
      "status": "open|done|canceled(可选)",
      "priority": "p0|p1|p2(可选)",
      "owner": "user|analyst|architect|developer|tester|system(可选)",
      "reason": "原因(可选)",
      "evidence_append": ["用户原话片段"]
    }
  ],
  "cancel": [
    {
      "id": "t1",
      "reason": "取消原因(可选)",
      "evidence_append": ["用户原话片段"]
    }
  ]
}
"""


def build_delta_prompt(
    *,
    current_snapshot: RequirementTodoSnapshot | None,
    user_messages: list[str],
    current_user_message: str,
) -> str:
    if current_snapshot is None:
        raise ValueError("current_snapshot 不能为空：RequirementTodoSnapshot 必须包含 session_id")
    keep_origin = get_keep_origin()
    recent_k = get_evidence_limit()
    snapshot_dict = build_snapshot_view(
        snapshot=current_snapshot, keep_origin=keep_origin, recent_k=recent_k
    )
    turn_index = _derive_turn_index(current_snapshot=current_snapshot)
    message_id = _derive_message_id(
        session_id=current_snapshot.session_id,
        turn_index=turn_index,
        message_text=current_user_message,
    )
    payload = {
        "current_snapshot": snapshot_dict,
        "user_messages": user_messages[-20:],
        "current_user_message": current_user_message,
        "current_user_turn_index": turn_index,
        "current_message_id": message_id,
    }
    return (
        _REQUIREMENT_TODO_DELTA_SYSTEM_PROMPT
        + "\n\n输入 JSON：\n"
        + json.dumps(payload, ensure_ascii=False)
    )


async def llm_delta(
    *,
    llm: Any,
    current_snapshot: RequirementTodoSnapshot,
    user_messages: list[str],
    current_user_message: str,
) -> RequirementTodoDelta:
    """
    调用 LLM 生成 RequirementTodoDelta（暂不与多智能体编排集成）

    约束：
    - 本函数只负责调用与解析，不负责重试策略、SSE 展示与状态写回
    - llm 约定：支持 `ainvoke(messages: list[BaseMessage])` 并返回带 `content` 的对象
    """

    prompt = build_delta_prompt(
        current_snapshot=current_snapshot,
        user_messages=user_messages,
        current_user_message=current_user_message,
    )
    messages: list[BaseMessage] = [SystemMessage(content=prompt)]
    response = await llm.ainvoke(messages)
    content = getattr(response, "content", None)
    return parse_delta(str(content or ""))


async def llm_update_snapshot(
    *,
    llm: Any,
    current_snapshot: RequirementTodoSnapshot,
    user_messages: list[str],
    current_user_message: str,
) -> tuple[RequirementTodoSnapshot, RequirementTodoDelta]:
    """
    一步完成：LLM 产出 delta + 确定性合并为新 snapshot

    返回：
    - new_snapshot：合并后的 TODO 快照（revision 已 +1）
    - delta：本轮变更（用于 SSE 展示或审计）
    """

    delta = await llm_delta(
        llm=llm,
        current_snapshot=current_snapshot,
        user_messages=user_messages,
        current_user_message=current_user_message,
    )
    turn_index = _derive_turn_index(current_snapshot=current_snapshot)
    message_id = _derive_message_id(
        session_id=current_snapshot.session_id,
        turn_index=turn_index,
        message_text=current_user_message,
    )
    new_snapshot = apply_delta(
        snapshot=current_snapshot,
        delta=delta,
        source_turn_index=turn_index,
        source_message_id=message_id,
    )
    return new_snapshot, delta


def parse_delta(text: str) -> RequirementTodoDelta:
    """
    严格解析 LLM 输出（必须是纯 JSON object）
    """

    raw = (text or "").strip()
    if not raw:
        raise ValueError("LLM 输出为空（期望 RequirementTodoDelta JSON）")
    if "```" in raw:
        raise ValueError("LLM 输出包含代码块标记（必须输出纯 JSON）")
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as e:
        raise ValueError("LLM 输出不是合法 JSON（必须输出纯 JSON）") from e
    if not isinstance(parsed, dict):
        raise ValueError("LLM 输出必须是 JSON object")
    return RequirementTodoDelta(**parsed)


def apply_delta(
    *,
    snapshot: RequirementTodoSnapshot,
    delta: RequirementTodoDelta,
    source_turn_index: int | None = None,
    source_message_id: str | None = None,
) -> RequirementTodoSnapshot:
    """
    确定性合并（不依赖 LLM）
    """

    base = snapshot
    keep_origin = get_keep_origin()
    recent_k = get_evidence_limit()

    if delta.reset:
        if delta.update or delta.cancel:
            raise ValueError("reset=true 时不允许同时包含 update/cancel")

        reset_items: list[RequirementTodoItem] = []
        for item in delta.add:
            base_history = list(item.evidence_history or [])
            incoming = [
                EvidenceAnchor(
                    kind=EvidenceKind.origin,
                    quote=q,
                    turn_index=source_turn_index,
                    message_id=source_message_id,
                )
                for q in item.evidence
            ]
            history = _merge_evidence_history(base_history, incoming)
            view = _evidence_view(history=history, keep_origin=keep_origin, recent_k=recent_k)
            reset_items.append(
                item.model_copy(update={"evidence_history": history, "evidence": view})
            )

        return RequirementTodoSnapshot(
            session_id=base.session_id, revision=base.revision + 1, items=reset_items
        )

    items_by_id: dict[str, RequirementTodoItem] = {
        i.id: _normalize_item_history(item=i, keep_origin=keep_origin, recent_k=recent_k)
        for i in base.items
    }

    for item in delta.add:
        if item.id in items_by_id:
            raise ValueError(f"add 冲突：TODO id 已存在: {item.id}（请用 update 修改）")
        base_history = list(item.evidence_history or [])
        incoming = [
            EvidenceAnchor(
                kind=EvidenceKind.origin,
                quote=q,
                turn_index=source_turn_index,
                message_id=source_message_id,
            )
            for q in item.evidence
        ]
        history = _merge_evidence_history(base_history, incoming)
        view = _evidence_view(history=history, keep_origin=keep_origin, recent_k=recent_k)
        items_by_id[item.id] = item.model_copy(
            update={"evidence_history": history, "evidence": view}
        )

    for patch in delta.update:
        current = items_by_id.get(patch.id)
        if not current:
            raise ValueError(f"update 引用不存在的 TODO id: {patch.id}")

        if current.status == TodoStatus.canceled:
            if any(v is not None for v in (patch.title, patch.priority, patch.owner)):
                raise ValueError(f"update 不允许修改已 canceled 的 TODO: {patch.id}")
            if patch.status is not None and patch.status != TodoStatus.canceled:
                raise ValueError(f"update 不允许修改已 canceled 的 TODO: {patch.id}")

        incoming = [
            EvidenceAnchor(
                kind=EvidenceKind.update,
                quote=q,
                turn_index=source_turn_index,
                message_id=source_message_id,
            )
            for q in list(patch.evidence_append or [])
        ]
        merged_history = _merge_evidence_history(list(current.evidence_history or []), incoming)
        view = _evidence_view(history=merged_history, keep_origin=keep_origin, recent_k=recent_k)
        updated = current.model_copy(
            update={
                "title": patch.title if patch.title is not None else current.title,
                "status": patch.status if patch.status is not None else current.status,
                "priority": patch.priority if patch.priority is not None else current.priority,
                "owner": patch.owner if patch.owner is not None else current.owner,
                "reason": patch.reason if patch.reason is not None else current.reason,
                "evidence_history": merged_history,
                "evidence": view,
            }
        )
        items_by_id[patch.id] = updated

    for cancel in delta.cancel:
        current = items_by_id.get(cancel.id)
        if not current:
            raise ValueError(f"cancel 引用不存在的 TODO id: {cancel.id}")
        incoming = [
            EvidenceAnchor(
                kind=EvidenceKind.cancel,
                quote=q,
                turn_index=source_turn_index,
                message_id=source_message_id,
            )
            for q in list(cancel.evidence_append or [])
        ]
        merged_history = _merge_evidence_history(list(current.evidence_history or []), incoming)
        view = _evidence_view(history=merged_history, keep_origin=keep_origin, recent_k=recent_k)
        updated = current.model_copy(
            update={
                "status": TodoStatus.canceled,
                "reason": cancel.reason or current.reason,
                "evidence_history": merged_history,
                "evidence": view,
            }
        )
        items_by_id[cancel.id] = updated

    merged_items = list(items_by_id.values())
    merged_items.sort(key=lambda x: x.id)
    return RequirementTodoSnapshot(
        session_id=base.session_id, revision=base.revision + 1, items=merged_items
    )


# ==================== 两层 TODO 存储 ====================

AgentId = Literal[
    "analyst_agent",
    "architect_agent",
    "developer_agent",
    "tester_agent",
]


class RequirementTodoStore(BaseModel):
    """
    两层 TODO 存储

    - global_snapshot：全局需求 TODO（跨 Agent 一致）
    - scoped_snapshots：按 Agent 隔离的 TODO（每个 Agent 一份）
    """

    session_id: str = Field(..., description="会话ID（强制）")
    global_snapshot: RequirementTodoSnapshot = Field(..., description="全局需求 TODO 快照（强制）")
    scoped_snapshots: dict[AgentId, RequirementTodoSnapshot] = Field(default_factory=dict)


def get_scoped_snapshot(
    *, store: RequirementTodoStore | None, agent_id: AgentId
) -> RequirementTodoSnapshot:
    if store is None:
        raise ValueError("store 不能为空：RequirementTodoStore 必须包含 session_id")
    snap = store.scoped_snapshots.get(agent_id)
    if snap is None:
        return RequirementTodoSnapshot(session_id=store.session_id)
    return snap


def apply_scoped_delta(
    *,
    store: RequirementTodoStore | None,
    agent_id: AgentId,
    delta: RequirementTodoDelta,
) -> RequirementTodoStore:
    if store is None:
        raise ValueError("store 不能为空：RequirementTodoStore 必须包含 session_id")
    base = store
    current = base.scoped_snapshots.get(agent_id) or RequirementTodoSnapshot(
        session_id=base.session_id
    )
    updated = apply_delta(snapshot=current, delta=delta)
    scoped = dict(base.scoped_snapshots)
    scoped[agent_id] = updated
    return base.model_copy(update={"scoped_snapshots": scoped})


def apply_global_delta(
    *,
    store: RequirementTodoStore | None,
    delta: RequirementTodoDelta,
) -> RequirementTodoStore:
    if store is None:
        raise ValueError("store 不能为空：RequirementTodoStore 必须包含 session_id")
    base = store
    updated = apply_delta(snapshot=base.global_snapshot, delta=delta)
    return base.model_copy(update={"global_snapshot": updated})
