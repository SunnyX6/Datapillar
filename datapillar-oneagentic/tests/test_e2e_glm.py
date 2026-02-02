from __future__ import annotations

import importlib.util
import os

import pytest
from pydantic import BaseModel
from datapillar_oneagentic import AgentContext, Datapillar, DatapillarConfig, Process, agent, tool
from datapillar_oneagentic.events import EventType
from datapillar_oneagentic.knowledge import (
    KnowledgeChunkConfig,
    KnowledgeConfig,
    KnowledgeIngestor,
    KnowledgeSource,
)
from datapillar_oneagentic.providers.llm import EmbeddingProvider
from datapillar_oneagentic.storage import create_knowledge_store


class TextOutput(BaseModel):
    text: str


JSON_OUTPUT_RULE = (
    "If you provide a final answer, return JSON only with a single field \"text\". "
    "When using function/tool calling or structured output, set \"text\" to the plain final string; "
    "do not embed JSON inside \"text\". "
    "Do not include any other content."
)


@tool
def echo(text: str) -> str:
    """Echo text.

    Args:
        text: input text

    Returns:
        echoed result
    """
    return f"echo:{text}"


class _StubSparseEmbedder:
    async def embed_text(self, text: str) -> dict[int, float]:
        return {len(text): 1.0}

    async def embed_texts(self, texts: list[str]) -> list[dict[int, float]]:
        return [{len(text): 1.0} for text in texts]


def _require_glm() -> dict:
    api_key = os.getenv("GLM_API_KEY")
    model = os.getenv("GLM_MODEL")
    if not api_key or not model:
        pytest.skip("GLM_API_KEY or GLM_MODEL is not set")

    config_kwargs: dict = {
        "provider": "glm",
        "api_key": api_key,
        "model": model,
        "temperature": 0.0,
    }
    base_url = os.getenv("GLM_BASE_URL")
    if base_url:
        config_kwargs["base_url"] = base_url

    config_kwargs["enable_thinking"] = False

    return config_kwargs


def _require_glm_config() -> DatapillarConfig:
    return DatapillarConfig(llm=_require_glm())


def _require_glm2() -> dict:
    api_key = os.getenv("GLM_EMBEDDING_API_KEY")
    model = os.getenv("GLM_EMBEDDING_MODEL")
    dimension_raw = os.getenv("GLM_EMBEDDING_DIMENSION")
    if not api_key or not model or not dimension_raw:
        pytest.skip("GLM_EMBEDDING_API_KEY/GLM_EMBEDDING_MODEL/GLM_EMBEDDING_DIMENSION is not set")

    try:
        dimension = int(dimension_raw)
    except ValueError as exc:
        raise ValueError("GLM_EMBEDDING_DIMENSION must be an integer") from exc

    config_kwargs: dict = {
        "provider": "glm",
        "api_key": api_key,
        "model": model,
        "dimension": dimension,
    }
    base_url = os.getenv("GLM_EMBEDDING_BASE_URL")
    if base_url:
        config_kwargs["base_url"] = base_url

    return config_kwargs


def _module_available(module_name: str) -> bool:
    return importlib.util.find_spec(module_name) is not None


def _select_vector() -> dict:
    if _module_available("lancedb") and _module_available("pyarrow"):
        return {"type": "lance", "path": "./data/test-experience"}
    if _module_available("chromadb"):
        return {"type": "chroma", "path": "./data/test-chroma"}
    pytest.skip("vector_store backend (lancedb/pyarrow or chromadb) is not available")


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


@pytest.mark.asyncio
async def test_glm_sequential() -> None:
    llm_config = _require_glm()
    embedding_config = _require_glm2()
    vector_store = _select_vector()
    namespace = "ns_glm_seq"
    knowledge_config = KnowledgeConfig(
        namespaces=[namespace],
        embedding=embedding_config,
        vector_store=vector_store,
    )
    config = DatapillarConfig(
        llm=llm_config,
    )
    chunk_config = KnowledgeChunkConfig(mode="general", general={"max_tokens": 2000, "overlap": 0})
    source = KnowledgeSource(
        source="Always include token KNO123 in the final answer.",
        chunk=chunk_config,
        doc_uid="doc1",
        name="Demo Knowledge",
        source_type="doc",
        filename="kb_doc_1.txt",
        metadata={"title": "Demo Doc"},
    )
    sparse_embedder = _StubSparseEmbedder()
    embedding_provider = EmbeddingProvider(knowledge_config.embedding)
    knowledge_store = create_knowledge_store(
        namespace,
        vector_store_config=knowledge_config.vector_store,
        embedding_config=knowledge_config.embedding,
    )
    ingestor = KnowledgeIngestor(
        store=knowledge_store,
        embedding_provider=embedding_provider,
    )
    await ingestor.ingest(sources=[source], sparse_embedder=sparse_embedder)

    @agent(
        id="alpha",
        name="Alpha",
        deliverable_schema=TextOutput,
        tools=[echo],
        description="Use tools and follow knowledge.",
    )
    class AlphaAgent:
        SYSTEM_PROMPT = (
            "You must call the echo tool with input 'hello'. "
            "If you see token KNO123 in knowledge, include it in the text field. "
            f"{JSON_OUTPUT_RULE}"
        )

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    @agent(
        id="beta",
        name="Beta",
        deliverable_schema=TextOutput,
        description="Read alpha output and summarize.",
    )
    class BetaAgent:
        SYSTEM_PROMPT = (
            "Prefix the text with 'beta:' and include the alpha output. "
            f"{JSON_OUTPUT_RULE}"
        )

        async def run(self, ctx: AgentContext) -> TextOutput:
            alpha = await ctx.get_deliverable("alpha") or {}
            messages = (
                ctx.messages()
                .system(f"{self.SYSTEM_PROMPT}\nAlpha output: {alpha.get('text', '')}")
                .user(ctx.query)
            )
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=config,
        namespace=namespace,
        name="glm_seq",
        agents=[AlphaAgent, BetaAgent],
        process=Process.SEQUENTIAL,
        knowledge=knowledge_config,
    )

    events = await _collect_events(team, query="run tool and follow knowledge", session_id="s_glm_seq")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) == {"alpha", "beta"}
    assert "echo:" in deliverables["alpha"]["text"]
    assert "KNO123" in deliverables["alpha"]["text"]
    assert deliverables["beta"]["text"].startswith("beta:")


@pytest.mark.asyncio
async def test_glm_dynamic() -> None:
    config = _require_glm_config()

    @agent(
        id="manager",
        name="Manager",
        deliverable_schema=TextOutput,
        description="Delegate tasks only.",
    )
    class ManagerAgent:
        SYSTEM_PROMPT = (
            "You must call delegate_to_worker. "
            "Do not answer directly. "
            f"{JSON_OUTPUT_RULE}"
        )

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            await ctx.invoke_tools(messages)
            return TextOutput(text="delegated")

    @agent(
        id="worker",
        name="Worker",
        deliverable_schema=TextOutput,
        tools=[echo],
        description="Handle delegated task and reply.",
    )
    class WorkerAgent:
        SYSTEM_PROMPT = f"Call echo tool. {JSON_OUTPUT_RULE}"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=config,
        namespace="ns_glm_dynamic",
        name="glm_dynamic",
        agents=[ManagerAgent, WorkerAgent],
        process=Process.DYNAMIC,
    )

    events = await _collect_events(team, query="delegate this task to worker", session_id="s_glm_dynamic")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) in ({"manager"}, {"worker"})
    agent_id = next(iter(deliverables))
    assert deliverables[agent_id].get("text", "").strip()


@pytest.mark.asyncio
async def test_glm_hierarchical() -> None:
    config = _require_glm_config()

    @agent(
        id="manager",
        name="Manager",
        deliverable_schema=TextOutput,
        description="Delegate to worker, then summarize.",
    )
    class ManagerAgent:
        SYSTEM_PROMPT = (
            "You must call delegate_to_worker when no worker result exists. "
            "After you get worker output, respond with JSON only. "
            f"{JSON_OUTPUT_RULE}"
        )

        async def run(self, ctx: AgentContext) -> TextOutput:
            worker = await ctx.get_deliverable("worker")
            if worker:
                messages = (
                    ctx.messages()
                    .system(f"{self.SYSTEM_PROMPT}\nWorker output: {worker.get('text', '')}\n")
                    .user(ctx.query)
                )
                output = await ctx.get_structured_output(messages)
                return output
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            await ctx.invoke_tools(messages)
            return TextOutput(text="delegated")

    @agent(
        id="worker",
        name="Worker",
        deliverable_schema=TextOutput,
        tools=[echo],
        description="Handle delegated task and reply.",
    )
    class WorkerAgent:
        SYSTEM_PROMPT = f"Call echo tool. {JSON_OUTPUT_RULE}"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=config,
        namespace="ns_glm_hier",
        name="glm_hier",
        agents=[ManagerAgent, WorkerAgent],
        process=Process.HIERARCHICAL,
    )

    events = await _collect_events(
        team,
        query=(
            "Please delegate to worker and summarize the following text: "
            "Datapillar is a data development product that provides task orchestration, "
            "metric management, and access control, with a focus on observability and cost governance."
        ),
        session_id="s_glm_hier",
    )
    deliverables = _extract_deliverables(events)
    assert set(deliverables.keys()) == {"manager", "worker"}
    assert deliverables["manager"].get("text", "").strip()
    assert deliverables["worker"].get("text", "").strip()


@pytest.mark.asyncio
async def test_glm_mapreduce() -> None:
    config = _require_glm_config()

    @agent(
        id="worker_a",
        name="WorkerA",
        deliverable_schema=TextOutput,
        tools=[echo],
        description="Handle part A.",
    )
    class WorkerAgentA:
        SYSTEM_PROMPT = f"Call echo tool. {JSON_OUTPUT_RULE}"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    @agent(
        id="worker_b",
        name="WorkerB",
        deliverable_schema=TextOutput,
        tools=[echo],
        description="Handle part B.",
    )
    class WorkerAgentB:
        SYSTEM_PROMPT = f"Call echo tool. {JSON_OUTPUT_RULE}"

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    @agent(
        id="reducer",
        name="Reducer",
        deliverable_schema=TextOutput,
        description="Aggregate map results.",
    )
    class ReducerAgent:
        SYSTEM_PROMPT = (
            "You are the result aggregator. Output JSON only; no Markdown, code blocks, or extra text. "
            f"{JSON_OUTPUT_RULE}"
        )

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=config,
        namespace="ns_glm_mr",
        name="glm_mr",
        agents=[WorkerAgentA, WorkerAgentB, ReducerAgent],
        process=Process.MAPREDUCE,
    )

    events = await _collect_events(team, query="Summarize two key points about data quality.", session_id="s_glm_mr")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) == {"reducer"}
    assert isinstance(deliverables["reducer"].get("text"), str)


@pytest.mark.asyncio
async def test_glm_react() -> None:
    config = _require_glm_config()

    @agent(
        id="react_worker",
        name="ReactWorker",
        deliverable_schema=TextOutput,
        tools=[echo],
        description="Execute planned steps.",
    )
    class ReactWorker:
        SYSTEM_PROMPT = (
            "Call echo tool with any text. "
            f"{JSON_OUTPUT_RULE}"
        )

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=config,
        namespace="ns_glm_react",
        name="glm_react",
        agents=[ReactWorker],
        process=Process.REACT,
    )

    events = await _collect_events(team, query="Create a simple plan and execute it.", session_id="s_glm_react")
    deliverables = _extract_deliverables(events)

    assert set(deliverables.keys()) == {"react_worker"}
    assert isinstance(deliverables["react_worker"].get("text"), str)


@pytest.mark.asyncio
async def test_glm_interrupt() -> None:
    config = _require_glm_config()

    @agent(
        id="interruptor",
        name="Interruptor",
        deliverable_schema=TextOutput,
        description="Ask for input and continue.",
    )
    class InterruptAgent:
        SYSTEM_PROMPT = f"{JSON_OUTPUT_RULE}"

        async def run(self, ctx: AgentContext) -> TextOutput:
            reply = ctx.interrupt("need input")
            messages = ctx.messages().system(f"{self.SYSTEM_PROMPT}\nUser reply: {reply}")
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=config,
        namespace="ns_glm_interrupt",
        name="glm_interrupt",
        agents=[InterruptAgent],
        process=Process.SEQUENTIAL,
    )

    events = await _collect_events(team, query="start", session_id="s_glm_interrupt")
    interrupt_event = next(e for e in events if e["event"] == "agent.interrupt")
    assert interrupt_event["data"]["interrupt"]["payload"] == "need input"
    assert interrupt_event["data"]["interrupt"]["interrupt_id"]

    resume_events = await _collect_events(
        team,
        session_id="s_glm_interrupt",
        resume_value="yes",
    )
    deliverables = _extract_deliverables(resume_events)

    assert "interruptor" in deliverables
    assert isinstance(deliverables["interruptor"].get("text"), str)


@pytest.mark.asyncio
async def test_glm_todo() -> None:
    config = _require_glm_config()

    @agent(
        id="todo_agent",
        name="TodoAgent",
        deliverable_schema=TextOutput,
        description="Report todo progress and reply.",
    )
    class TodoAgent:
        SYSTEM_PROMPT = (
            "If no todo list exists, call plan_todo to create at least 2 items. "
            "You must call report_todo for each pending todo item and mark it completed. "
            f"{JSON_OUTPUT_RULE}"
        )

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            messages = await ctx.invoke_tools(messages)
            output = await ctx.get_structured_output(messages)
            return output

    team = Datapillar(
        config=config,
        namespace="ns_glm_todo",
        name="glm_todo",
        agents=[TodoAgent],
        process=Process.SEQUENTIAL,
    )

    events = await _collect_events(
        team,
        query=(
            "This is a multi-phase task. "
            "Break it into at least 2 todo items and track progress."
        ),
        session_id="s_glm_todo",
    )
    deliverables = _extract_deliverables(events)

    assert "todo_agent" in deliverables
    assert isinstance(deliverables["todo_agent"].get("text"), str)


@pytest.mark.asyncio
async def test_glm_experience() -> None:
    llm_config = _require_glm()
    embedding_config = _require_glm2()
    vector_store = _select_vector()
    config = DatapillarConfig(
        llm=llm_config,
        embedding=embedding_config,
        vector_store=vector_store,
    )

    @agent(
        id="rag_agent",
        name="RagAgent",
        deliverable_schema=TextOutput,
        description="Use retrieved experience context.",
    )
    class RagAgent:
        SYSTEM_PROMPT = (
            "If you see token RAG_TAG=RAG123 in context, include it in the text field. "
            f"{JSON_OUTPUT_RULE}"
        )

        async def run(self, ctx: AgentContext) -> TextOutput:
            messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
            rag_injected = any(
                msg.role == "system" and "RAG_TAG=RAG123" in msg.content
                for msg in messages
            )
            output = await ctx.get_structured_output(messages)
            suffix = "RAG_INJECTED=true RAG_TAG=RAG123" if rag_injected else "RAG_INJECTED=false"
            return TextOutput(text=f"{output.text} {suffix}")

    team = Datapillar(
        config=config,
        namespace="ns_glm_rag",
        name="glm_rag",
        agents=[RagAgent],
        process=Process.SEQUENTIAL,
        enable_learning=True,
    )

    seed_session = "s_glm_rag_seed"
    await _collect_events(team, query="Summarize a data quality incident.", session_id=seed_session)
    saved = await team.save_experience(
        session_id=seed_session,
        feedback={"RAG_TAG": "RAG123"},
    )
    assert saved is True

    events = await _collect_events(
        team,
        query="Summarize a data quality incident.",
        session_id="s_glm_rag_query",
    )
    deliverables = _extract_deliverables(events)
    output_text = deliverables["rag_agent"]["text"]
    assert "RAG_INJECTED=true" in output_text
    assert "RAG_TAG=RAG123" in output_text
