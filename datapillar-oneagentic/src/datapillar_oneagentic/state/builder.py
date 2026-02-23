# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
StateBuilder - Blackboard state writer (V1).

Goals:
- No field-level reads/writes at call sites; use module facades for grouped updates
- Centralized patch generation (one node, one patch)
- Strict handling for reducer fields (messages/mapreduce_results) to avoid duplicates
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Iterable, Mapping

from langgraph.types import Overwrite

from datapillar_oneagentic.context.timeline.timeline import Timeline
from datapillar_oneagentic.core.status import ExecutionStatus
from datapillar_oneagentic.core.types import SessionKey
from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.messages.adapters.langchain import from_langchain, to_langchain
from datapillar_oneagentic.messages.adapters.langgraph import remove_all_messages
from datapillar_oneagentic.state.blackboard import Blackboard

BLACKBOARD_KEYS = frozenset(Blackboard.__annotations__.keys())


def _validate_key(key: str) -> None:
    if key not in BLACKBOARD_KEYS:
        raise KeyError(f"Invalid Blackboard key: {key}")


def _as_dict(value: Any) -> dict | None:
    return dict(value) if isinstance(value, dict) else None


def _as_list(value: Any) -> list:
    return list(value) if isinstance(value, list) else []


@dataclass(frozen=True, slots=True)
class RoutingSnapshot:
    active_agent: str | None
    assigned_task: str | None
    last_status: ExecutionStatus | None
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
    Patch generator (centralized write semantics).

    Regular fields:
    - Last write wins

    Reducer fields:
    - messages: add_messages reducer, supports append/replace
    - mapreduce_results: operator.add reducer, supports append/reset only
    """

    def __init__(self, state: dict[str, Any]) -> None:
        self._state = state
        self._patch: dict[str, Any] = {}

        # Modules exposed to callers.
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
            raise KeyError(f"{key} cannot use set; use the dedicated APIs")
        self._state[key] = value
        self._patch[key] = value

    def append_messages(self, messages: Messages) -> None:
        _validate_key("messages")
        if not messages:
            return
        langchain_messages = to_langchain(messages)
        current = _as_list(self._state.get("messages"))
        current.extend(langchain_messages)
        self._state["messages"] = current

        existing = self._patch.get("messages")
        if existing is None:
            self._patch["messages"] = list(langchain_messages)
            return
        if not isinstance(existing, list):
            raise TypeError("messages patch conflict: existing patch is not a list")
        existing.extend(langchain_messages)
        self._patch["messages"] = existing

    def replace_messages(self, messages: Messages) -> None:
        _validate_key("messages")
        cleaned = to_langchain(messages)
        self._state["messages"] = cleaned
        patch_messages = [remove_all_messages()]
        patch_messages.extend(cleaned)
        self._patch["messages"] = patch_messages

    def reset_mapreduce_results(self) -> None:
        _validate_key("mapreduce_results")
        self._state["mapreduce_results"] = []
        # operator.add reducer field: reset must use Overwrite.
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
            raise TypeError("mapreduce_results patch conflict: existing patch is not a list")
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
        # Flush timeline if dirty.
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
        """Build initial state for a new session (full state, not a patch)."""
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
        """Build incremental patch for a resumed session."""
        sb = cls(dict(state))
        sb.memory.append_user_message(query)
        sb.routing.activate(entry_agent_id)
        return sb.patch()

    # === Only for writing user replies into runtime state (interrupt resume) ===
    def append_reply_state(self, resume_value: Any) -> None:
        if isinstance(resume_value, str):
            content = resume_value
        else:
            try:
                content = json.dumps(resume_value, ensure_ascii=False)
            except Exception:
                content = str(resume_value)
        # Runtime writes bypass patch (out of graph) and update state.messages directly.
        filtered = MemoryModule._strip_system_messages([Message.user(content)])
        if not filtered:
            return
        current = _as_list(self._state.get("messages"))
        current.extend(to_langchain(Messages(filtered)))
        self._state["messages"] = current


class MemoryModule:
    """messages (checkpoint memory) module: clean/append/replace and text helpers."""

    def __init__(self, *, state: dict[str, Any], patch: StateBuilder) -> None:
        self._state = state
        self._patch = patch

    def snapshot(self) -> Messages:
        raw = self._state.get("messages") or []
        messages = from_langchain(_as_list(raw))
        filtered = self._strip_system_messages(messages)
        return Messages(filtered)

    def raw_snapshot(self) -> Messages:
        """For tool_call parsing only; avoid polluting normal memory access."""
        raw = _as_list(self._state.get("messages"))
        return Messages(from_langchain(raw))

    def has_messages(self) -> bool:
        return bool(self.snapshot())

    def append(
        self,
        messages: Messages,
        *,
        drop_tail_ai: bool = False,
    ) -> None:
        filtered = self._strip_system_messages(messages)
        if drop_tail_ai and filtered and filtered[-1].role == "assistant":
            filtered = filtered[:-1]
        if filtered:
            self._patch.append_messages(Messages(filtered))

    def replace(self, messages: Messages) -> None:
        filtered = self._strip_system_messages(messages)
        self._patch.replace_messages(Messages(filtered))

    def replace_runtime_only(self, messages: Messages) -> None:
        """
        Runtime-only: shorten messages for the next call without writing to checkpoint.

        Note: this method does not write to patch.
        """
        filtered = self._strip_system_messages(messages)
        self._state["messages"] = to_langchain(Messages(filtered))

    def append_tool_messages(self, messages: Messages) -> None:
        if not messages:
            return
        tool_messages = [msg for msg in messages if msg.role == "tool"]
        if not tool_messages:
            return
        self._patch.append_messages(Messages(tool_messages))

    def append_user_message(self, content: str) -> None:
        text = (content or "").strip()
        if not text:
            return
        self._patch.append_messages(Messages([Message.user(text)]))

    def append_user_reply(self, content: str) -> None:
        self.append_user_message(content)

    def append_task_instruction(self, *, task_id: str, description: str) -> None:
        if not task_id or not description:
            return
        self._patch.append_messages(
            Messages([Message.assistant(f"[TASK {task_id}] {description}", name="react_controller")])
        )

    def append_execution_summary(
        self,
        *,
        agent_id: str,
        execution_status: ExecutionStatus | str | None,
        error: str | None,
        deliverable_key: str | None,
    ) -> None:
        task_id = self._extract_task_latest(self.snapshot())
        msg = self._build_execution_summary(
            agent_id=agent_id,
            task_id=task_id,
            status=execution_status,
            error=error,
            deliverable_key=deliverable_key,
        )
        self._patch.append_messages(Messages([msg]))

    def latest_user_text(self) -> str | None:
        for msg in reversed(self.snapshot()):
            if msg.role == "user":
                content = self.extract_text(msg.content).strip()
                if content:
                    return content
        return None

    def latest_task_instruction(self) -> str | None:
        for msg in reversed(self.snapshot()):
            if msg.role == "assistant" and getattr(msg, "name", None) == "react_controller":
                content = self.extract_text(msg.content)
                if content.startswith("【TASK "):
                    return content
        return None

    @staticmethod
    def extract_text(content: str | None) -> str:
        if content is None:
            return ""
        return content if isinstance(content, str) else str(content)

    @staticmethod
    def _strip_system_messages(messages: list[Message]) -> list[Message]:
        cleaned: list[Message] = []
        for msg in messages:
            if msg.role == "system":
                continue
            if msg.metadata.get("internal") is True:
                continue
            sanitized = MemoryModule._sanitize_message(msg)
            if sanitized is not None:
                cleaned.append(sanitized)
        return cleaned

    @staticmethod
    def _sanitize_message(msg: Message) -> Message | None:
        if msg.role == "user":
            return Message.user(msg.content, name=msg.name, id=msg.id)
        if msg.role == "assistant":
            return Message.assistant(msg.content, name=msg.name, id=msg.id)
        return None

    @staticmethod
    def _extract_task_latest(messages: list[Message]) -> str | None:
        for msg in reversed(messages):
            if msg.role == "assistant" and getattr(msg, "name", None) == "react_controller":
                text = MemoryModule.extract_text(msg.content)
                if text.startswith("【TASK "):
                    try:
                        header = text.split("】", 1)[0]
                        return header.replace("【TASK ", "").strip()
                    except Exception:
                        return None
        return None

    @staticmethod
    def _build_execution_summary(
        *,
        agent_id: str,
        task_id: str | None,
        status: ExecutionStatus | str | None,
        error: str | None,
        deliverable_key: str | None,
    ) -> Message:
        parts = [f"【RESULT】agent={agent_id}"]
        if task_id:
            parts.append(f"task={task_id}")
        if status:
            status_value = status.value if hasattr(status, "value") else status
            parts.append(f"status={status_value}")
        if deliverable_key:
            parts.append(f"deliverable={deliverable_key}")
        if error:
            parts.append(f"error={error}")
        return Message.assistant(" ".join(parts), name="datapillar")


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
        last_error_value = self._state.get("last_agent_error")
        last_error = str(last_error_value) if last_error_value is not None else None

        return RoutingSnapshot(
            active_agent=active_agent,
            assigned_task=assigned_task,
            last_status=last_status if isinstance(last_status, ExecutionStatus) else last_status,
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
        error: str | None,
    ) -> None:
        self._patch.set("last_agent_status", status)
        self._patch.set("last_agent_error", error)

    def finish_agent(
        self,
        *,
        status: ExecutionStatus | None,
        error: str | None,
    ) -> None:
        self.set_last_result(status=status, error=error)
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

    def set_error_retry(self, count: int) -> None:
        self._patch.set("error_retry_count", int(count))

    def reset_error_retry(self) -> None:
        self.set_error_retry(0)

    def inc_error_retry(self) -> int:
        snap = self.snapshot()
        next_value = snap.error_retry_count + 1
        self.set_error_retry(next_value)
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
        value = self._state.get("compression_context")
        return str(value) if value is not None else None

    def persist_compression(self, summary: str | None) -> None:
        self._patch.set("compression_context", summary)

    def set_runtime_compression(self, summary: str | None) -> None:
        self._state["compression_context"] = summary


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
                self._timeline.add_entry_dict(event_data)
            except Exception:
                # TimelineRecorder output should be stable; guard against corrupt entries.
                continue
        self._dirty = True

    def flush(self) -> None:
        if not self._dirty or self._timeline is None:
            return
        payload = self._timeline.to_dict()
        self._patch.set("timeline", payload)
        self._dirty = False
