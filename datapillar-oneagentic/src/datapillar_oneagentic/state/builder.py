"""
StateBuilder - Blackboard 状态写入器（V1）

目标：
- 调用点禁止字段级读写；必须通过模块化门面一次性读写一组字段
- patch 生成集中管理（单节点单 patch）
- reducer 字段（messages/mapreduce_results）写入强约束，避免重复写入与重复累加
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Iterable, Mapping

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    RemoveMessage,
    ToolMessage,
    SystemMessage,
)
from langgraph.graph.message import REMOVE_ALL_MESSAGES
from langgraph.types import Overwrite

from datapillar_oneagentic.context.timeline.timeline import Timeline
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.state.blackboard import Blackboard


BLACKBOARD_KEYS = frozenset(Blackboard.__annotations__.keys())


def _validate_key(key: str) -> None:
    if key not in BLACKBOARD_KEYS:
        raise KeyError(f"非法 Blackboard key: {key}")


def _as_dict(value: Any) -> dict | None:
    return dict(value) if isinstance(value, dict) else None


def _as_list(value: Any) -> list:
    return list(value) if isinstance(value, list) else []


@dataclass(frozen=True, slots=True)
class RoutingSnapshot:
    active_agent: str | None
    assigned_task: str | None
    last_status: ExecutionStatus | None
    last_failure_kind: FailureKind | None
    last_error: str | None


@dataclass(frozen=True, slots=True)
class DeliverablesSnapshot:
    keys: list[str]


@dataclass(frozen=True, slots=True)
class TodoSnapshot:
    todo: dict | None


@dataclass(frozen=True, slots=True)
class ReactSnapshot:
    goal: str | None
    plan: dict | None
    reflection: dict | None
    error_retry_count: int


@dataclass(frozen=True, slots=True)
class MapReduceSnapshot:
    goal: str | None
    understanding: str | None
    tasks: list[dict]
    current_task: dict | None
    results: list[dict]


class StateBuilder:
    """
    patch 生成器（集中管理写入语义）

    普通字段：
    - 最后写入覆盖

    reducer 字段：
    - messages: add_messages reducer，支持 append/replace
    - mapreduce_results: operator.add reducer，仅支持 append/reset
    """

    def __init__(self, state: dict[str, Any]) -> None:
        self._state = state
        self._patch: dict[str, Any] = {}

        # modules（对调用点唯一暴露）
        self.memory = MemoryModule(state=state, patch=self)
        self.routing = RoutingModule(state=state, patch=self)
        self.deliverables = DeliverablesModule(state=state, patch=self)
        self.todo = TodoModule(state=state, patch=self)
        self.react = ReactModule(state=state, patch=self)
        self.mapreduce = MapReduceModule(state=state, patch=self)
        self.compression = CompressionModule(state=state, patch=self)
        self.timeline = TimelineModule(state=state, patch=self)

    def set(self, key: str, value: Any) -> None:
        _validate_key(key)
        if key in {"messages", "mapreduce_results"}:
            raise KeyError(f"{key} 禁止使用 set，请使用专用接口")
        self._state[key] = value
        self._patch[key] = value

    def append_messages(self, messages: list[BaseMessage]) -> None:
        _validate_key("messages")
        if not messages:
            return
        current = _as_list(self._state.get("messages"))
        current.extend(messages)
        self._state["messages"] = current

        existing = self._patch.get("messages")
        if existing is None:
            self._patch["messages"] = list(messages)
            return
        if not isinstance(existing, list):
            raise TypeError("messages patch 冲突：已存在非 list 的 messages patch")
        existing.extend(messages)
        self._patch["messages"] = existing

    def replace_messages(self, messages: list[BaseMessage]) -> None:
        _validate_key("messages")
        cleaned = list(messages)
        self._state["messages"] = cleaned
        patch_messages: list[BaseMessage] = [RemoveMessage(id=REMOVE_ALL_MESSAGES, content="")]
        patch_messages.extend(cleaned)
        self._patch["messages"] = patch_messages

    def reset_mapreduce_results(self) -> None:
        _validate_key("mapreduce_results")
        self._state["mapreduce_results"] = []
        # operator.add reducer 字段：清空必须用 Overwrite
        self._patch["mapreduce_results"] = Overwrite([])

    def append_mapreduce_results(self, items: Iterable[dict]) -> None:
        _validate_key("mapreduce_results")
        new_items = [dict(item) for item in items] if items else []
        if not new_items:
            return
        current = _as_list(self._state.get("mapreduce_results"))
        current.extend(new_items)
        self._state["mapreduce_results"] = current

        existing = self._patch.get("mapreduce_results")
        if existing is None:
            self._patch["mapreduce_results"] = list(new_items)
            return
        if not isinstance(existing, list):
            raise TypeError("mapreduce_results patch 冲突：已存在非 list 的 patch")
        existing.extend(new_items)
        self._patch["mapreduce_results"] = existing

    @property
    def namespace(self) -> str:
        value = self._state.get("namespace")
        return str(value) if value is not None else ""

    @property
    def session_id(self) -> str:
        value = self._state.get("session_id")
        return str(value) if value is not None else ""

    def key(self) -> SessionKey:
        return SessionKey(namespace=self.namespace, session_id=self.session_id)

    def resolve_agent_query(self) -> str:
        routing = self.routing.snapshot()
        if routing.assigned_task:
            return routing.assigned_task
        if self.react.snapshot().plan is not None:
            task = self.memory.latest_task_instruction()
            if task:
                return task
        latest = self.memory.latest_user_text()
        return latest or ""

    def patch(self) -> dict[str, Any]:
        # timeline 按 dirty flush
        self.timeline.flush()
        return dict(self._patch)

    @classmethod
    def build_initial_state(
        cls,
        *,
        namespace: str,
        session_id: str,
        query: str,
        entry_agent_id: str,
    ) -> dict[str, Any]:
        """构造新会话初始 state（完整 state，而非 patch）。"""
        from datapillar_oneagentic.state.blackboard import create_blackboard

        state = create_blackboard(
            namespace=namespace,
            session_id=session_id,
        )
        sb = cls(state)
        sb.memory.append_user_message(query)
        sb.routing.activate(entry_agent_id)
        return state

    @classmethod
    def build_resume_update(
        cls,
        *,
        state: dict[str, Any],
        query: str,
        entry_agent_id: str,
    ) -> dict[str, Any]:
        """构造续聊的增量 patch（用于恢复后的继续执行）。"""
        sb = cls(dict(state))
        sb.memory.append_user_message(query)
        sb.routing.activate(entry_agent_id)
        return sb.patch()

    # === 仅用于将 user reply 写入运行态 state（interrupt 恢复）===
    def append_user_reply_inplace(self, resume_value: Any) -> None:
        if isinstance(resume_value, str):
            content = resume_value
        else:
            try:
                content = json.dumps(resume_value, ensure_ascii=False)
            except Exception:
                content = str(resume_value)
        # 运行态写入不走 patch（不在图内），直接更新 state.messages
        filtered = MemoryModule._strip_system_messages([HumanMessage(content=content)])
        if not filtered:
            return
        current = _as_list(self._state.get("messages"))
        current.extend(filtered)
        self._state["messages"] = current


class MemoryModule:
    """messages（checkpoint 记忆）模块：清洗/追加/覆盖与常用文本提取。"""

    def __init__(self, *, state: dict[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch

    def snapshot(self) -> list[BaseMessage]:
        raw = self._state.get("messages") or []
        return self._strip_system_messages(_as_list(raw))

    def raw_snapshot(self) -> list[BaseMessage]:
        """仅供 tool_call 解析使用，避免污染常规记忆读取路径。"""
        return _as_list(self._state.get("messages"))

    def has_messages(self) -> bool:
        return bool(self.snapshot())

    def append(
        self,
        messages: list[BaseMessage],
        *,
        drop_tail_ai: bool = False,
    ) -> None:
        filtered = self._strip_system_messages(messages)
        if drop_tail_ai and filtered and isinstance(filtered[-1], AIMessage):
            filtered = filtered[:-1]
        if filtered:
            self._patch.append_messages(filtered)

    def replace(self, messages: list[BaseMessage]) -> None:
        filtered = self._strip_system_messages(messages)
        self._patch.replace_messages(filtered)

    def replace_runtime_only(self, messages: list[BaseMessage]) -> None:
        """
        仅用于 runtime：缩短下一次调用的 messages，不写入 checkpoint。

        注意：该方法不会写入 patch。
        """
        filtered = self._strip_system_messages(messages)
        self._state["messages"] = list(filtered)

    def append_tool_messages(self, messages: list[BaseMessage]) -> None:
        if not messages:
            return
        tool_messages = [msg for msg in messages if isinstance(msg, ToolMessage)]
        if not tool_messages:
            return
        self._patch.append_messages(tool_messages)

    def append_user_message(self, content: str) -> None:
        text = (content or "").strip()
        if not text:
            return
        self._patch.append_messages([HumanMessage(content=text)])

    def append_user_reply(self, content: str) -> None:
        self.append_user_message(content)

    def append_task_instruction(self, *, task_id: str, description: str) -> None:
        if not task_id or not description:
            return
        self._patch.append_messages(
            [
                AIMessage(
                    content=f"【TASK {task_id}】{description}",
                    name="react_controller",
                )
            ]
        )

    def append_execution_summary(
        self,
        *,
        agent_id: str,
        execution_status: ExecutionStatus | str | None,
        failure_kind: FailureKind | str | None,
        error: str | None,
        deliverable_key: str | None,
    ) -> None:
        task_id = self._extract_latest_task_id(self.snapshot())
        msg = self._build_execution_summary_message(
            agent_id=agent_id,
            task_id=task_id,
            status=execution_status,
            failure_kind=failure_kind,
            error=error,
            deliverable_key=deliverable_key,
        )
        self._patch.append_messages([msg])

    def latest_user_text(self) -> str | None:
        for msg in reversed(self.snapshot()):
            if isinstance(msg, HumanMessage):
                content = self.extract_text(msg.content).strip()
                if content:
                    return content
        return None

    def latest_task_instruction(self) -> str | None:
        for msg in reversed(self.snapshot()):
            if isinstance(msg, AIMessage) and getattr(msg, "name", None) == "react_controller":
                content = self.extract_text(msg.content)
                if content.startswith("【TASK "):
                    return content
        return None

    @staticmethod
    def extract_text(content: str | list | None) -> str:
        if content is None:
            return ""
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            texts: list[str] = []
            for item in content:
                if isinstance(item, str):
                    texts.append(item)
                elif isinstance(item, dict) and item.get("type") == "text":
                    texts.append(str(item.get("text", "")))
            return "\n".join(texts)
        return str(content)

    @staticmethod
    def _strip_system_messages(messages: list[BaseMessage]) -> list[BaseMessage]:
        cleaned: list[BaseMessage] = []
        for msg in messages:
            if isinstance(msg, SystemMessage):
                continue
            if (getattr(msg, "additional_kwargs", {}) or {}).get("internal"):
                continue
            sanitized = MemoryModule._sanitize_message(msg)
            if sanitized is not None:
                cleaned.append(sanitized)
        return cleaned

    @staticmethod
    def _sanitize_message(msg: BaseMessage) -> BaseMessage | None:
        if isinstance(msg, HumanMessage):
            return HumanMessage(
                content=msg.content,
                name=getattr(msg, "name", None) or None,
                id=getattr(msg, "id", None),
            )
        if isinstance(msg, AIMessage):
            return AIMessage(
                content=msg.content,
                name=getattr(msg, "name", None) or None,
                id=getattr(msg, "id", None),
            )
        return None

    @staticmethod
    def _extract_latest_task_id(messages: list[BaseMessage]) -> str | None:
        for msg in reversed(messages):
            if isinstance(msg, AIMessage) and getattr(msg, "name", None) == "react_controller":
                text = MemoryModule.extract_text(msg.content)
                if text.startswith("【TASK "):
                    try:
                        header = text.split("】", 1)[0]
                        return header.replace("【TASK ", "").strip()
                    except Exception:
                        return None
        return None

    @staticmethod
    def _build_execution_summary_message(
        *,
        agent_id: str,
        task_id: str | None,
        status: ExecutionStatus | str | None,
        failure_kind: FailureKind | str | None,
        error: str | None,
        deliverable_key: str | None,
    ) -> AIMessage:
        parts = [f"【RESULT】agent={agent_id}"]
        if task_id:
            parts.append(f"task={task_id}")
        if status:
            status_value = status.value if hasattr(status, "value") else status
            parts.append(f"status={status_value}")
        if failure_kind:
            kind_value = failure_kind.value if hasattr(failure_kind, "value") else failure_kind
            parts.append(f"failure_kind={kind_value}")
        if deliverable_key:
            parts.append(f"deliverable={deliverable_key}")
        if error:
            parts.append(f"error={error}")
        return AIMessage(content=" ".join(parts), name="datapillar")


class RoutingModule:
    def __init__(self, *, state: Mapping[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch

    def snapshot(self) -> RoutingSnapshot:
        active_value = self._state.get("active_agent")
        active_agent = str(active_value) if active_value is not None else None

        task_value = self._state.get("assigned_task")
        assigned_task = str(task_value) if task_value is not None else None

        last_status = self._state.get("last_agent_status")
        last_failure_kind = self._state.get("last_agent_failure_kind")
        last_error_value = self._state.get("last_agent_error")
        last_error = str(last_error_value) if last_error_value is not None else None

        return RoutingSnapshot(
            active_agent=active_agent,
            assigned_task=assigned_task,
            last_status=last_status if isinstance(last_status, ExecutionStatus) else last_status,
            last_failure_kind=(
                last_failure_kind if isinstance(last_failure_kind, FailureKind) else last_failure_kind
            ),
            last_error=last_error,
        )

    def activate(self, agent_id: str | None) -> None:
        self._patch.set("active_agent", agent_id)

    def clear_active(self) -> None:
        self.activate(None)

    def assign_task(self, task: str | None) -> None:
        self._patch.set("assigned_task", task)

    def clear_task(self) -> None:
        self.assign_task(None)

    def set_last_result(
        self,
        *,
        status: ExecutionStatus | None,
        failure_kind: FailureKind | None,
        error: str | None,
    ) -> None:
        self._patch.set("last_agent_status", status)
        self._patch.set("last_agent_failure_kind", failure_kind)
        self._patch.set("last_agent_error", error)

    def finish_agent(
        self,
        *,
        status: ExecutionStatus | None,
        failure_kind: FailureKind | None,
        error: str | None,
    ) -> None:
        self.set_last_result(status=status, failure_kind=failure_kind, error=error)
        self.clear_active()
        self.clear_task()


class DeliverablesModule:
    def __init__(self, *, state: Mapping[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch

    def snapshot(self) -> DeliverablesSnapshot:
        value = self._state.get("deliverable_keys") or []
        keys = [str(item) for item in value if item] if isinstance(value, list) else []
        return DeliverablesSnapshot(keys=keys)

    def replace(self, keys: list[str]) -> None:
        self._patch.set("deliverable_keys", list(keys or []))

    def clear(self) -> None:
        self.replace([])

    def record_saved(self, key: str) -> None:
        if not key:
            return
        snapshot = self.snapshot()
        if key in snapshot.keys:
            return
        keys = list(snapshot.keys)
        keys.append(key)
        self.replace(keys)


class TodoModule:
    def __init__(self, *, state: Mapping[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch

    def snapshot(self) -> TodoSnapshot:
        todo = _as_dict(self._state.get("todo"))
        return TodoSnapshot(todo=todo)

    def replace(self, todo: dict | None) -> None:
        self._patch.set("todo", todo)

    def clear(self) -> None:
        self.replace(None)

class ReactModule:
    def __init__(self, *, state: Mapping[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch

    def snapshot(self) -> ReactSnapshot:
        goal_value = self._state.get("goal")
        goal = str(goal_value) if goal_value is not None else None
        plan = _as_dict(self._state.get("plan"))
        reflection = _as_dict(self._state.get("reflection"))
        value = self._state.get("error_retry_count")
        error_retry_count = int(value) if isinstance(value, int) else 0
        return ReactSnapshot(
            goal=goal,
            plan=plan,
            reflection=reflection,
            error_retry_count=error_retry_count,
        )

    def save_goal(self, goal: str | None) -> None:
        self._patch.set("goal", goal)

    def save_plan(self, plan: dict | None) -> None:
        self._patch.set("plan", plan)

    def save_reflection(self, reflection: dict | None) -> None:
        self._patch.set("reflection", reflection)

    def set_error_retry_count(self, count: int) -> None:
        self._patch.set("error_retry_count", int(count))

    def reset_error_retry(self) -> None:
        self.set_error_retry_count(0)

    def inc_error_retry(self) -> int:
        snap = self.snapshot()
        next_value = snap.error_retry_count + 1
        self.set_error_retry_count(next_value)
        return next_value


class MapReduceModule:
    def __init__(self, *, state: Mapping[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch

    def snapshot(self) -> MapReduceSnapshot:
        goal_value = self._state.get("mapreduce_goal")
        goal = str(goal_value) if goal_value is not None else None
        understanding_value = self._state.get("mapreduce_understanding")
        understanding = str(understanding_value) if understanding_value is not None else None
        tasks = [dict(item) for item in _as_list(self._state.get("mapreduce_tasks"))]
        current_task = _as_dict(self._state.get("mapreduce_task"))
        results = [dict(item) for item in _as_list(self._state.get("mapreduce_results"))]
        return MapReduceSnapshot(
            goal=goal,
            understanding=understanding,
            tasks=tasks,
            current_task=current_task,
            results=results,
        )

    def init_plan(self, *, goal: str, understanding: str, tasks: list[dict]) -> None:
        self._patch.set("mapreduce_goal", goal)
        self._patch.set("mapreduce_understanding", understanding)
        self._patch.set("mapreduce_tasks", list(tasks))
        self._patch.set("mapreduce_task", None)
        self._patch.reset_mapreduce_results()

    def set_current_task(self, task: dict | None) -> None:
        self._patch.set("mapreduce_task", task)

    def append_results(self, results: Iterable[dict]) -> None:
        self._patch.append_mapreduce_results(results)


class CompressionModule:
    def __init__(self, *, state: Mapping[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch

    def snapshot(self) -> str | None:
        value = self._state.get("compression__context")
        return str(value) if value is not None else None

    def persist_compression(self, summary: str | None) -> None:
        self._patch.set("compression__context", summary)

    def set_runtime_compression(self, summary: str | None) -> None:
        self._state["compression__context"] = summary


class TimelineModule:
    def __init__(self, *, state: Mapping[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch
        self._dirty = False
        raw = state.get("timeline")
        self._timeline = Timeline.from_dict(raw) if isinstance(raw, dict) else None

    def snapshot(self) -> dict | None:
        if self._timeline is None:
            return None
        return self._timeline.to_dict()

    def record_events(self, events: list[dict]) -> None:
        if not events:
            return
        if self._timeline is None:
            self._timeline = Timeline()
        for event_data in events:
            try:
                self._timeline.add_entry_from_dict(event_data)
            except Exception:
                # TimelineRecorder 输出应是稳定格式；这里仅做保护，避免单条坏数据污染整个节点。
                continue
        self._dirty = True

    def flush(self) -> None:
        if not self._dirty or self._timeline is None:
            return
        payload = self._timeline.to_dict()
        self._patch.set("timeline", payload)
        self._dirty = False
