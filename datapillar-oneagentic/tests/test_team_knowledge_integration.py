from __future__ import annotations

from pydantic import BaseModel

import datapillar_oneagentic.storage as storage_module
from datapillar_oneagentic import AgentContext, Datapillar, DatapillarConfig, Process, agent
from datapillar_oneagentic.knowledge import (
    Knowledge,
    KnowledgeConfig,
    KnowledgeInject,
    KnowledgeRetrieve,
    KnowledgeSource,
)


class _Output(BaseModel):
    answer: str


def test_datapillar_merges(monkeypatch) -> None:
    @agent(
        id="agent_alpha",
        name="Alpha",
        deliverable_schema=_Output,
        knowledge=Knowledge(
            sources=[KnowledgeSource(source_id="kb_agent", name="AgentKB", source_type="doc")],
            retrieve=KnowledgeRetrieve(top_k=2, inject=KnowledgeInject(format="json")),
        ),
    )
    class AgentAlpha:
        async def run(self, ctx: AgentContext) -> _Output:
            return _Output(answer="ok")

    @agent(
        id="agent_beta",
        name="Beta",
        deliverable_schema=_Output,
    )
    class AgentBeta:
        async def run(self, ctx: AgentContext) -> _Output:
            return _Output(answer="ok")

    monkeypatch.setattr(storage_module, "create_knowledge_store", lambda *args, **kwargs: object())

    knowledge_config = KnowledgeConfig(
        base_config={
            "embedding": {
                "api_key": "stub",
                "model": "text-embedding-3-small",
                "provider": "openai",
                "dimension": 2,
            },
            "vector_store": {"type": "lance", "path": "./data/vectors"},
        }
    )
    config = DatapillarConfig(
        llm={"api_key": "stub", "model": "stub", "provider": "openai"},
        knowledge=knowledge_config,
    )
    team_knowledge = Knowledge(
        sources=[KnowledgeSource(source_id="kb_team", name="TeamKB", source_type="doc")],
        retrieve=KnowledgeRetrieve(top_k=5, inject=KnowledgeInject(mode="system", max_tokens=800)),
    )

    team = Datapillar(
        config=config,
        namespace="ns_team",
        name="team",
        agents=[AgentAlpha, AgentBeta],
        process=Process.SEQUENTIAL,
        knowledge=team_knowledge,
    )

    alpha_spec = next(spec for spec in team._agent_specs if spec.id == "agent_alpha")
    beta_spec = next(spec for spec in team._agent_specs if spec.id == "agent_beta")

    assert alpha_spec.knowledge is not None
    assert [s.source_id for s in alpha_spec.knowledge.sources] == ["kb_team", "kb_agent"]
    assert alpha_spec.knowledge.retrieve is not None
    assert alpha_spec.knowledge.retrieve.top_k == 2
    assert alpha_spec.knowledge.retrieve.inject is not None
    assert alpha_spec.knowledge.retrieve.inject.mode == "system"
    assert alpha_spec.knowledge.retrieve.inject is not None
    assert alpha_spec.knowledge.retrieve.inject.format == "json"

    assert beta_spec.knowledge is not None
    assert [s.source_id for s in beta_spec.knowledge.sources] == ["kb_team"]
    assert beta_spec.knowledge.retrieve is not None
    assert beta_spec.knowledge.retrieve.top_k == 5
