from __future__ import annotations

import pytest
from pydantic import BaseModel

from datapillar_oneagentic import AgentContext, Datapillar, DatapillarConfig, Process, agent, tool
from datapillar_oneagentic.events import EventType
from datapillar_oneagentic.providers.llm.llm import LLMFactory
from tests.stub_llm import StubLlmConfig, make_stub_factory


class TextOutput(BaseModel):
    text: str = "ok"


class WorkerOutput(BaseModel):
    text: str = "worker_done"


async def _collect_events(
    team: Datapillar,
    *,
    session_id: str,
    query: str | None = None,
    resume_value: str | None = None,
) -> list[dict]:
    events: list[dict] = []
    async for event in team.stream(
        query=query,
        session_id=session_id,
        resume_value=resume_value,
    ):
        events.append(event)
    return events


def _stub_config() -> DatapillarConfig:
    return DatapillarConfig(llm={"api_key": "stub", "model": "stub", "provider": "openai"})


def _extract_deliverables(events: list[dict]) -> dict:
    deliverables: dict[str, dict] = {}
    for event in events:
        if event.get("event") != EventType.AGENT_END.value:
            continue
        agent = event.get("agent") or {}
        agent_id = agent.get("id")
        deliverable = event.get("data", {}).get("deliverable")
        if agent_id and deliverable is not None:
            deliverables[agent_id] = deliverable
    return deliverables


@tool
def echo(text: str) -> str:
    """Echo text.

    Args:
        text: input text

    Returns:
        echoed result
    """
    return f"echo:{text}"


@pytest.mark.asyncio
async def test_stub_sequential(monkeypatch) -> None:
    monkeypatch.setattr(
        LLMFactory,
        "create_chat_model",
        make_stub_factory(StubLlmConfig(tool_text="tool_result")),
    )

    @agent(
        id="alpha",
        name="Alpha",
        deliverable_schema=TextOutput,
        tools=[echo],
    )
    class AlphaAgent:
        SYSTEM_PROMPT = "alpha"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    @agent(
        id="beta",
        name="Beta",
        deliverable_schema=TextOutput,
    )
    class BetaAgent:
        SYSTEM_PROMPT = "beta"

        async def run(self, ctx: AgentContext) -> TextOutput:
            alpha = await ctx.get_deliverable("alpha") or {}
            return TextOutput(text=f"beta:{alpha.get('text', '')}")

    team = Datapillar(
        config=_stub_config(),
        namespace="ns_seq",
        name="seq",
        agents=[AlphaAgent, BetaAgent],
        process=Process.SEQUENTIAL,
    )

    events = await _collect_events(team, query="go", session_id="s1")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) == {"alpha", "beta"}
    assert deliverables["alpha"]["text"] == "tool_result"
    assert deliverables["beta"]["text"].startswith("beta:")


@pytest.mark.asyncio
async def test_stub_dynamic(monkeypatch) -> None:
    monkeypatch.setattr(
        LLMFactory,
        "create_chat_model",
        make_stub_factory(
            StubLlmConfig(
                tool_text="worker_done",
                delegate_targets=["worker"],
            )
        ),
    )

    @agent(
        id="manager",
        name="Manager",
        deliverable_schema=TextOutput,
    )
    class ManagerAgent:
        SYSTEM_PROMPT = "manager"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            await ctx.invoke_tools(messages)
            return TextOutput(text="delegated")

    @agent(
        id="worker",
        name="Worker",
        deliverable_schema=WorkerOutput,
    )
    class WorkerAgent:
        SYSTEM_PROMPT = "worker"

        async def run(self, ctx: AgentContext) -> WorkerOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=_stub_config(),
        namespace="ns_dynamic",
        name="dynamic",
        agents=[ManagerAgent, WorkerAgent],
        process=Process.DYNAMIC,
    )

    events = await _collect_events(team, query="run", session_id="s2")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) == {"worker"}
    assert deliverables["worker"]["text"] == "worker_done"


@pytest.mark.asyncio
async def test_stub_hierarchical(monkeypatch) -> None:
    monkeypatch.setattr(
        LLMFactory,
        "create_chat_model",
        make_stub_factory(
            StubLlmConfig(
                tool_text="worker_done",
                delegate_targets=["worker"],
            )
        ),
    )

    @agent(
        id="manager",
        name="Manager",
        deliverable_schema=TextOutput,
    )
    class ManagerAgent:
        SYSTEM_PROMPT = "manager"

        async def run(self, ctx: AgentContext) -> TextOutput:
            worker = await ctx.get_deliverable("worker")
            if worker:
                return TextOutput(text=f"manager:{worker.get('text', '')}")
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            await ctx.invoke_tools(messages)
            return TextOutput(text="delegated")

    @agent(
        id="worker",
        name="Worker",
        deliverable_schema=WorkerOutput,
    )
    class WorkerAgent:
        SYSTEM_PROMPT = "worker"

        async def run(self, ctx: AgentContext) -> WorkerOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=_stub_config(),
        namespace="ns_hier",
        name="hier",
        agents=[ManagerAgent, WorkerAgent],
        process=Process.HIERARCHICAL,
    )

    events = await _collect_events(team, query="run", session_id="s3")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) == {"manager", "worker"}
    assert deliverables["worker"]["text"] == "worker_done"
    assert deliverables["manager"]["text"].startswith("manager:")


@pytest.mark.asyncio
async def test_stub_mapreduce(monkeypatch) -> None:
    monkeypatch.setattr(LLMFactory, "create_chat_model", make_stub_factory())

    @agent(
        id="worker_a",
        name="WorkerA",
        deliverable_schema=WorkerOutput,
    )
    class WorkerAgentA:
        SYSTEM_PROMPT = "worker_a"

        async def run(self, ctx: AgentContext) -> WorkerOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    @agent(
        id="worker_b",
        name="WorkerB",
        deliverable_schema=WorkerOutput,
    )
    class WorkerAgentB:
        SYSTEM_PROMPT = "worker_b"

        async def run(self, ctx: AgentContext) -> WorkerOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    @agent(
        id="reducer",
        name="Reducer",
        deliverable_schema=TextOutput,
    )
    class ReducerAgent:
        SYSTEM_PROMPT = "reducer"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=_stub_config(),
        namespace="ns_mr",
        name="mr",
        agents=[WorkerAgentA, WorkerAgentB, ReducerAgent],
        process=Process.MAPREDUCE,
    )

    events = await _collect_events(team, query="summarize", session_id="s4")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) == {"reducer"}
    assert deliverables["reducer"]["text"] == "ok"


@pytest.mark.asyncio
async def test_stub_react(monkeypatch) -> None:
    monkeypatch.setattr(
        LLMFactory,
        "create_chat_model",
        make_stub_factory(StubLlmConfig(tool_text="ok")),
    )

    @agent(
        id="react_worker",
        name="ReactWorker",
        deliverable_schema=TextOutput,
    )
    class ReactWorker:
        SYSTEM_PROMPT = "react_worker"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=_stub_config(),
        namespace="ns_react",
        name="react",
        agents=[ReactWorker],
        process=Process.REACT,
    )

    events = await _collect_events(team, query="plan", session_id="s5")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) == {"react_worker"}
    assert deliverables["react_worker"]["text"] == "ok"


@pytest.mark.asyncio
async def test_stub_interrupt(monkeypatch) -> None:
    monkeypatch.setattr(LLMFactory, "create_chat_model", make_stub_factory())

    @agent(
        id="interruptor",
        name="Interruptor",
        deliverable_schema=TextOutput,
    )
    class InterruptAgent:
        SYSTEM_PROMPT = "interruptor"

        async def run(self, ctx: AgentContext) -> TextOutput:
            reply = ctx.interrupt("need input")
            return TextOutput(text=f"reply:{reply}")

    team = Datapillar(
        config=_stub_config(),
        namespace="ns_interrupt",
        name="interrupt",
        agents=[InterruptAgent],
        process=Process.SEQUENTIAL,
    )

    events = await _collect_events(team, query="start", session_id="s6")
    interrupt_event = next(e for e in events if e["event"] == "agent.interrupt")
    assert interrupt_event["data"]["interrupt"]["payload"] == "need input"
    assert interrupt_event["data"]["interrupt"]["interrupt_id"]

    resume_events = await _collect_events(team, session_id="s6", resume_value="yes")
    deliverables = _extract_deliverables(resume_events)

    assert deliverables["interruptor"]["text"] == "reply:yes"
