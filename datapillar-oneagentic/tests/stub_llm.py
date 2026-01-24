from __future__ import annotations

from dataclasses import dataclass, field
import re
from types import UnionType
from typing import Any, Sequence, Union, get_args, get_origin

from langchain_core.messages import AIMessage, BaseMessage, SystemMessage, ToolMessage
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


def _extract_agent_ids(messages: Sequence[BaseMessage]) -> list[str]:
    ids: list[str] = []
    for msg in messages:
        if isinstance(msg, SystemMessage):
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


def _make_ai_message(content: str, tool_calls: list[dict[str, Any]] | None = None) -> AIMessage:
    if tool_calls is None:
        return AIMessage(content=content)

    try:
        return AIMessage(content=content, tool_calls=tool_calls)
    except TypeError:
        msg = AIMessage(content=content, additional_kwargs={"tool_calls": tool_calls})
        try:
            object.__setattr__(msg, "tool_calls", tool_calls)
        except Exception:
            setattr(msg, "tool_calls", tool_calls)
        return msg


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

    async def ainvoke(self, messages: Sequence[BaseMessage], _config: dict | None = None, **_kwargs) -> Any:
        if self._tools:
            return self._invoke_with_tools(messages)
        if self._structured_schema is not None:
            return self._build_structured(messages)
        return AIMessage(content="{}")

    def _invoke_with_tools(self, _messages: Sequence[BaseMessage]) -> AIMessage:
        tool_call = self._select_tool_call()
        if tool_call:
            self._tool_call_count += 1
            return _make_ai_message("", tool_calls=[tool_call])
        return _make_ai_message(self._final_tool_response())

    def _final_tool_response(self) -> str:
        return f"{{\"text\": \"{self._config.tool_text}\"}}"

    def _select_tool_call(self) -> dict[str, Any] | None:
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

    def _build_tool_call(self, name: str, args: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": f"call_{self._tool_call_count + 1}",
            "name": name,
            "args": args,
            "type": "tool_call",
        }

    def _build_structured(self, messages: Sequence[BaseMessage]) -> BaseModel:
        schema = self._structured_schema
        if schema is None:
            return BaseModel()

        if schema is PlannerOutput:
            agent_ids = _extract_agent_ids(messages)
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
            agent_ids = _extract_agent_ids(messages)
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
            return _build_schema_with_text(schema, self._config.tool_text)

        return _build_default_schema(schema)


def _has_tool_messages(messages: Sequence[BaseMessage]) -> bool:
    for msg in messages:
        if isinstance(msg, ToolMessage):
            return True
    return False


def _build_schema_with_text(schema: type[BaseModel], text: str) -> BaseModel:
    try:
        return schema(text=text)
    except Exception:
        fields = getattr(schema, "model_fields", None) or getattr(schema, "__fields__", {})
        if "text" not in fields:
            return _build_default_schema(schema)
        data = {"text": text}
        return schema.model_validate(data)
