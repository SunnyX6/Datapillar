from __future__ import annotations

import pytest
from pydantic import BaseModel

from datapillar_oneagentic import AgentContext, Datapillar, DatapillarConfig, Process, agent
from datapillar_oneagentic.providers.llm.llm import LLMFactory
from tests.stub_llm import StubLlmConfig, make_stub_factory


class TextOutput(BaseModel):
    text: str = "ok"


async def _collect_events(team: Datapillar, *, query: str, session_id: str) -> list[dict]:
    events: list[dict] = []
    async for event in team.stream(query=query, session_id=session_id):
        events.append(event)
    return events


def _stub_config() -> DatapillarConfig:
    return DatapillarConfig(llm={"api_key": "stub", "model": "stub", "provider": "openai"})


@pytest.mark.asyncio
async def test_stub_todo_flow(monkeypatch) -> None:
    monkeypatch.setattr(
        LLMFactory,
        "create_chat_model",
        make_stub_factory(
            StubLlmConfig(
                use_todo_plan=True,
                use_todo_tool=True,
                todo_items=["step1", "step2"],
                tool_text="todo_done",
            )
        ),
    )

    @agent(
        id="todo_agent",
        name="TodoAgent",
        deliverable_schema=TextOutput,
    )
    class TodoAgent:
        SYSTEM_PROMPT = "todo_agent"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.build_messages(self.SYSTEM_PROMPT)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=_stub_config(),
        namespace="ns_todo",
        name="todo",
        agents=[TodoAgent],
        process=Process.SEQUENTIAL,
    )

    events = await _collect_events(team, query="need todo", session_id="s_todo")
    todo_events = [e for e in events if e["event"] == "todo.update"]

    assert len(todo_events) >= 1

    latest = todo_events[-1]["todo"]
    assert [item["id"] for item in latest["items"]] == ["t2"]


@pytest.mark.asyncio
async def test_stub_todo_flow_complex_task(monkeypatch) -> None:
    monkeypatch.setattr(
        LLMFactory,
        "create_chat_model",
        make_stub_factory(
            StubLlmConfig(
                use_todo_plan=True,
                use_todo_tool=True,
                todo_items=["phase1", "phase2", "phase3"],
                tool_text="todo_done",
            )
        ),
    )

    @agent(
        id="todo_agent",
        name="TodoAgent",
        deliverable_schema=TextOutput,
    )
    class TodoAgent:
        SYSTEM_PROMPT = "todo_agent"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.build_messages(self.SYSTEM_PROMPT)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=_stub_config(),
        namespace="ns_todo_complex",
        name="todo_complex",
        agents=[TodoAgent],
        process=Process.SEQUENTIAL,
    )

    events = await _collect_events(
        team,
        query="Complex task: design, build, validate, and report results.",
        session_id="s_todo_complex",
    )
    todo_events = [e for e in events if e["event"] == "todo.update"]

    assert len(todo_events) >= 1

    latest = todo_events[-1]["todo"]
    assert [item["id"] for item in latest["items"]] == ["t2", "t3"]
