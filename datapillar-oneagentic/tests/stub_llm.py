from __future__ import annotations

from dataclasses import dataclass, field
import re
from types import UnionType
from typing import Any, Sequence, Union, cast, get_args, get_origin

from pydantic import BaseModel

from datapillar_oneagentic.core.graphs.mapreduce.schemas import (
    MapReducePlannerOutput,
    MapReduceTaskOutput,
)
from datapillar_oneagentic.core.graphs.react.schemas import (
    PlannerOutput,
    PlanTaskOutput,
    ReflectorOutput,
)
from datapillar_oneagentic.messages import Message, Messages, ToolCall
from datapillar_oneagentic.messages.adapters.langchain import from_langchain, is_langchain_list
from datapillar_oneagentic.todo.tool import TODO_PLAN_TOOL_NAME, TODO_TOOL_NAME


@dataclass(slots=True)
class StubLlmConfig:
    use_todo_tool: bool = False
    use_todo_plan: bool = False
    todo_items: list[str] = field(default_factory=lambda: ["step1", "step2"])
    todo_goal: str = "stub-goal"
    todo_plan_op: str = "replace"
    tool_text: str = "stub"
    delegate_task: str = "delegate task"
    delegate_targets: list[str] = field(default_factory=list)


def make_stub_factory(config: StubLlmConfig | None = None):
    def _factory(_provider_config):
        return StubChatModel(config=config)
    return _factory


def _coerce_messages(messages: Sequence[Any]) -> Messages:
    if isinstance(messages, Messages):
        return messages
    if isinstance(messages, list) and is_langchain_list(messages):
        return cast(Messages, from_langchain(messages))
    if isinstance(messages, tuple):
        as_list = list(messages)
        if is_langchain_list(as_list):
            return cast(Messages, from_langchain(as_list))
    if isinstance(messages, (list, tuple)) and all(isinstance(item, Message) for item in messages):
        return Messages(messages)
    raise TypeError("StubChatModel only accepts Messages or LangChain message lists")


def _extract_system_agent_ids(messages: Messages) -> list[str]:
    ids: list[str] = []
    for msg in messages:
        if msg.role == "system":
            ids.extend(re.findall(r"\*\*([a-z0-9_]+)\*\*", msg.content))
    return ids


def _dummy_value(annotation: Any) -> Any:
    origin = get_origin(annotation)
    args = get_args(annotation)

    if origin is list:
        return []
    if origin is dict:
        return {}

    if annotation in (str,):
        return "ok"
    if annotation in (int,):
        return 1
    if annotation in (float,):
        return 0.0
    if annotation in (bool,):
        return True

    if origin in (Union, UnionType):
        for item in args:
            if item is not type(None):
                return _dummy_value(item)
    return None


def _build_default_schema(schema: type[BaseModel]) -> BaseModel:
    try:
        return schema()
    except Exception:
        fields = getattr(schema, "model_fields", None) or getattr(schema, "__fields__", {})
        data = {}
        for name, info in fields.items():
            annotation = getattr(info, "annotation", None)
            data[name] = _dummy_value(annotation)
        return schema.model_validate(data)


def _make_ai_message(content: str, tool_calls: list[ToolCall] | None = None) -> Message:
    if tool_calls is None:
        return Message.assistant(content)
    return Message.assistant(content, tool_calls=tool_calls)


class StubChatModel:
    def __init__(
        self,
        *,
        config: StubLlmConfig | None = None,
        structured_schema: type[BaseModel] | None = None,
        tools: Sequence[Any] | None = None,
    ) -> None:
        self._config = config or StubLlmConfig()
        self._structured_schema = structured_schema
        self._tools = list(tools or [])
        self._tool_call_count = 0

    def bind(self, **_kwargs) -> "StubChatModel":
        return self

    def with_structured_output(self, schema: type[BaseModel], **_kwargs) -> "StubChatModel":
        return StubChatModel(
            config=self._config,
            structured_schema=schema,
            tools=self._tools,
        )

    def bind_tools(self, tools: Sequence[Any], **_kwargs) -> "StubChatModel":
        return StubChatModel(
            config=self._config,
            structured_schema=self._structured_schema,
            tools=tools,
        )

    async def ainvoke(self, messages: Sequence[Any], _config: dict | None = None, **_kwargs) -> Any:
        normalized = _coerce_messages(messages)
        if self._tools:
            return self._invoke_with_tools(normalized)
        if self._structured_schema is not None:
            return self._build_structured(normalized)
        return Message.assistant("{}")

    def _invoke_with_tools(self, _messages: Messages) -> Message:
        tool_call = self._select_tool_call()
        if tool_call:
            self._tool_call_count += 1
            return _make_ai_message("", tool_calls=[tool_call])
        return _make_ai_message(self._final_tool_response())

    def _final_tool_response(self) -> str:
        return f"{{\"text\": \"{self._config.tool_text}\"}}"

    def _select_tool_call(self) -> ToolCall | None:
        if not self._tools:
            return None

        delegate_targets = set(self._config.delegate_targets or [])
        if delegate_targets and self._tool_call_count == 0:
            for tool in self._tools:
                if tool.name.startswith("delegate_to_"):
                    target = tool.name.removeprefix("delegate_to_")
                    if target in delegate_targets:
                        return self._build_tool_call(
                            tool.name,
                            {"task_description": self._config.delegate_task},
                        )

        if self._config.use_todo_plan:
            plan_tool = next((t for t in self._tools if t.name == TODO_PLAN_TOOL_NAME), None)
            if plan_tool and self._tool_call_count == 0:
                return self._build_tool_call(
                    plan_tool.name,
                    {
                        "op": self._config.todo_plan_op,
                        "items": list(self._config.todo_items),
                        "todo_ids": [],
                        "goal": self._config.todo_goal,
                    },
                )
            if self._config.use_todo_tool and self._tool_call_count == 1:
                todo_tool = next((t for t in self._tools if t.name == TODO_TOOL_NAME), None)
                if todo_tool:
                    return self._build_tool_call(
                        todo_tool.name,
                        {"todo_id": "t1", "status": "completed", "result": "ok"},
                    )
        elif self._config.use_todo_tool and self._tool_call_count == 0:
            todo_tool = next((t for t in self._tools if t.name == TODO_TOOL_NAME), None)
            if todo_tool:
                return self._build_tool_call(
                    todo_tool.name,
                    {"todo_id": "t1", "status": "completed", "result": "ok"},
                )

        if self._tool_call_count > 0:
            return None

        for tool in self._tools:
            if tool.name in {TODO_TOOL_NAME, TODO_PLAN_TOOL_NAME} or tool.name.startswith("delegate_to_"):
                continue
            return self._build_tool_call(tool.name, {"text": self._config.tool_text})

        return None

    def _build_tool_call(self, name: str, args: dict[str, Any]) -> ToolCall:
        return ToolCall(id=f"call_{self._tool_call_count + 1}", name=name, args=args)

    def _build_structured(self, messages: Messages) -> BaseModel:
        schema = self._structured_schema
        if schema is None:
            return BaseModel()

        if schema is PlannerOutput:
            agent_ids = _extract_system_agent_ids(messages)
            agent_id = agent_ids[0] if agent_ids else "agent"
            return PlannerOutput(
                understanding="stub",
                tasks=[
                    PlanTaskOutput(
                        description="do",
                        assigned_agent=agent_id,
                        depends_on=[],
                    )
                ],
            )

        if schema is ReflectorOutput:
            return ReflectorOutput(
                goal_achieved=True,
                confidence=0.9,
                summary="stub",
                issues=[],
                suggestions=[],
                next_action="complete",
                reason="stub",
            )

        if schema is MapReducePlannerOutput:
            agent_ids = _extract_system_agent_ids(messages)
            if not agent_ids:
                agent_ids = ["agent"]
            tasks = [
                MapReduceTaskOutput(
                    description=f"task {idx}",
                    agent_id=agent_id,
                    input=f"input {idx}",
                )
                for idx, agent_id in enumerate(agent_ids, 1)
            ]
            return MapReducePlannerOutput(understanding="stub", tasks=tasks)

        if _has_tool_messages(messages):
            return _build_schema(schema, self._config.tool_text)

        return _build_default_schema(schema)


def _has_tool_messages(messages: Messages) -> bool:
    for msg in messages:
        if msg.role == "tool":
            return True
    return False


def _build_schema(schema: type[BaseModel], text: str) -> BaseModel:
    try:
        return schema(text=text)
    except Exception:
        fields = getattr(schema, "model_fields", None) or getattr(schema, "__fields__", {})
        if "text" not in fields:
            return _build_default_schema(schema)
        data = {"text": text}
        return schema.model_validate(data)
