from __future__ import annotations

import pytest

from datapillar_oneagentic.experience.learner import ExperienceLearner, ExperienceRecord
from datapillar_oneagentic.experience.retriever import ExperienceRetriever


class _StubEmbeddingProvider:
    def __init__(self, *, vector=None, fail: bool = False) -> None:
        self._vector = vector or [0.1, 0.2]
        self._fail = fail
        self.last_text: str | None = None

    async def embed_text(self, text: str) -> list[float]:
        self.last_text = text
        if self._fail:
            raise RuntimeError("embedding failed")
        return list(self._vector)


class _StubExperienceStore:
    def __init__(
        self,
        records_by_outcome: dict[str | None, list[ExperienceRecord]] | None = None,
    ) -> None:
        self.records_by_outcome = records_by_outcome or {}
        self.added: list[ExperienceRecord] = []
        self.last_search: tuple[list[float], int, str | None] | None = None

    async def add(self, record: ExperienceRecord) -> str:
        self.added.append(record)
        return record.id

    async def search(
        self,
        query_vector: list[float],
        k: int = 5,
        outcome: str | None = None,
    ) -> list[ExperienceRecord]:
        self.last_search = (list(query_vector), k, outcome)
        return list(self.records_by_outcome.get(outcome, []))


@pytest.mark.asyncio
async def test_experience_learner_save_success() -> None:
    store = _StubExperienceStore()
    embedder = _StubEmbeddingProvider()
    learner = ExperienceLearner(store=store, namespace="ns", embedding_provider=embedder)

    learner.start_recording("s1", "目标1")
    learner.record_tool("s1", "tool_a")
    learner.record_agent("s1", "agent_a")
    learner.complete_recording("s1", "success", result_summary="完成")

    ok = await learner.save_experience("s1", feedback={"stars": 5})
    assert ok is True
    assert learner.has_pending("s1") is False
    assert store.added[0].feedback["stars"] == 5


@pytest.mark.asyncio
async def test_experience_learner_save_failure_keeps_pending() -> None:
    store = _StubExperienceStore()
    embedder = _StubEmbeddingProvider(fail=True)
    learner = ExperienceLearner(store=store, namespace="ns", embedding_provider=embedder)

    learner.start_recording("s1", "目标1")

    ok = await learner.save_experience("s1")
    assert ok is False
    assert learner.has_pending("s1") is True


@pytest.mark.asyncio
async def test_experience_learner_save_without_pending() -> None:
    store = _StubExperienceStore()
    embedder = _StubEmbeddingProvider()
    learner = ExperienceLearner(store=store, namespace="ns", embedding_provider=embedder)

    ok = await learner.save_experience("missing")
    assert ok is False


@pytest.mark.asyncio
async def test_experience_retriever_build_context_prefers_success() -> None:
    success = ExperienceRecord(
        id="s1",
        namespace="ns",
        session_id="s1",
        goal="目标1",
        outcome="success",
        tools_used=["tool_a"],
    )
    failure = ExperienceRecord(
        id="s2",
        namespace="ns",
        session_id="s2",
        goal="目标2",
        outcome="failure",
        tools_used=["tool_b"],
    )
    store = _StubExperienceStore({"success": [success], None: [failure]})
    embedder = _StubEmbeddingProvider()
    retriever = ExperienceRetriever(store=store, embedding_provider=embedder)

    context = await retriever.build_context("目标", k=2, prefer_success=True)
    assert "经验 1" in context
    assert "经验 2" in context
    assert "tool_a" in context


@pytest.mark.asyncio
async def test_experience_retriever_search_handles_embedding_failure() -> None:
    store = _StubExperienceStore()
    embedder = _StubEmbeddingProvider(fail=True)
    retriever = ExperienceRetriever(store=store, embedding_provider=embedder)

    records = await retriever.search("目标", k=3)
    assert records == []


@pytest.mark.asyncio
async def test_experience_retriever_common_tools_sorted() -> None:
    record_a = ExperienceRecord(
        id="s1",
        namespace="ns",
        session_id="s1",
        goal="目标1",
        outcome="success",
        tools_used=["tool_a", "tool_b"],
    )
    record_b = ExperienceRecord(
        id="s2",
        namespace="ns",
        session_id="s2",
        goal="目标2",
        outcome="success",
        tools_used=["tool_a"],
    )
    store = _StubExperienceStore({"success": [record_a, record_b]})
    embedder = _StubEmbeddingProvider()
    retriever = ExperienceRetriever(store=store, embedding_provider=embedder)

    tools = await retriever.get_common_tools("目标", k=2)
    assert tools[0] == "tool_a"
