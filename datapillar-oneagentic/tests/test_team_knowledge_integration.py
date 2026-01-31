from __future__ import annotations

from pydantic import BaseModel

import datapillar_oneagentic.storage as storage_module
from datapillar_oneagentic import AgentContext, Datapillar, DatapillarConfig, Process, agent
from datapillar_oneagentic.knowledge import (
    KnowledgeConfig,
)


class _Output(BaseModel):
    answer: str


def test_datapillar_knowledge_binding(monkeypatch) -> None:
    knowledge_config = KnowledgeConfig(
        namespaces=["kb_team"],
        embedding={
            "api_key": "stub",
            "model": "text-embedding-3-small",
            "provider": "openai",
            "dimension": 2,
        },
        vector_store={"type": "lance", "path": "./data/vectors"},
    )

    @agent(
        id="agent_alpha",
        name="Alpha",
        deliverable_schema=_Output,
        knowledge=knowledge_config,
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

    config = DatapillarConfig(
        llm={"api_key": "stub", "model": "stub", "provider": "openai"},
    )

    team_agent_only = Datapillar(
        config=config,
        namespace="ns_team",
        name="team_agent_only",
        agents=[AgentAlpha, AgentBeta],
        process=Process.SEQUENTIAL,
    )

    assert set(team_agent_only._knowledge_config_map.keys()) == {"agent_alpha"}

    team_all = Datapillar(
        config=config,
        namespace="ns_team_all",
        name="team_all",
        agents=[AgentAlpha, AgentBeta],
        process=Process.SEQUENTIAL,
        knowledge=knowledge_config,
    )

    assert set(team_all._knowledge_config_map.keys()) == {"agent_alpha", "agent_beta"}
