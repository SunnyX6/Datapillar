# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Knowledge RAG quickstart: team-level binding.

Run:
    uv run python examples/quickstart_knowledge_team_agent.py

Dependencies:
    pip install datapillar-oneagentic[lance,knowledge]

Requirements:
    1) LLM (Datapillar team execution)
       export DATAPILLAR_LLM_PROVIDER="openai"              # openai | anthropic | glm | deepseek | openrouter | ollama
       export DATAPILLAR_LLM_API_KEY="sk-xxx"
       export DATAPILLAR_LLM_MODEL="gpt-4o"
       # Optional: export DATAPILLAR_LLM_BASE_URL="https://api.openai.com/v1"
       # Optional: export DATAPILLAR_LLM_ENABLE_THINKING="true"
    2) Embedding (knowledge ingest and retrieval)
       export DATAPILLAR_EMBEDDING_PROVIDER="openai"        # openai | glm
       export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
       export DATAPILLAR_EMBEDDING_MODEL="text-embedding-3-small"
       export DATAPILLAR_EMBEDDING_DIMENSION="1536"
       # Optional: export DATAPILLAR_EMBEDDING_BASE_URL="https://api.openai.com/v1"

Notes:
    - source_id is generated automatically.
    - source accepts a file path, URL, raw text, or bytes.
    - Each document must provide its own chunk config.
    - namespace must be provided explicitly (store is bound inside Datapillar and ingest/retrieve).
    - Retrieval is scoped to the current namespace; cross-namespace retrieval is not supported.
"""

import asyncio
import json
import os

from pydantic import BaseModel

from datapillar_oneagentic import AgentContext, Datapillar, DatapillarConfig, Process, agent
from datapillar_oneagentic.providers.llm import EmbeddingBackend, Provider
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig, LLMConfig
from datapillar_oneagentic.knowledge import (
    KnowledgeChunkRequest,
    KnowledgeConfig,
    KnowledgeSource,
    KnowledgeService,
)


class AnswerOutput(BaseModel):
    answer: str


async def _prepare_demo_knowledge(service: KnowledgeService) -> None:

    chunk = {
        "mode": "general",
        "general": {"max_tokens": 200, "overlap": 40},
    }
    team_source = KnowledgeSource(
        source=(
            "Datapillar is a data development SaaS platform with knowledge chunking and retrieval augmentation.\n"
            "The knowledge framework is a foundational capability that can be attached to teams or agents."
        ),
        chunk=chunk,
        name="Team knowledge base",
        source_type="doc",
        filename="team.txt",
        metadata={"title": "Team knowledge example"},
    )
    agent_source = KnowledgeSource(
        source="Agents can layer personal knowledge on top of team knowledge and override retrieval settings.",
        chunk=chunk,
        name="Personal knowledge base",
        source_type="doc",
        filename="agent.txt",
        metadata={"title": "Personal knowledge example"},
    )

    await service.chunk(KnowledgeChunkRequest(sources=[team_source]), namespace=namespace)
    await service.chunk(KnowledgeChunkRequest(sources=[agent_source]), namespace=namespace)


@agent(
    id="team_agent",
    name="Team Knowledge User",
    deliverable_schema=AnswerOutput,
)
class TeamAgent:
    SYSTEM_PROMPT = (
        "You use team knowledge."
        "If the knowledge context contains the answer, quote it and respond succinctly.\n\n"
        "## Output requirements\n"
        "Return JSON only (single object), no explanations or Markdown:\n"
        '{"answer": "Your answer"}'
    )

    async def run(self, ctx: AgentContext) -> AnswerOutput:
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
        output = await ctx.get_structured_output(messages)
        return output


@agent(
    id="specialist_agent",
    name="Knowledge Specialist",
    deliverable_schema=AnswerOutput,
)
class SpecialistAgent:
    SYSTEM_PROMPT = (
        "You are a knowledge specialist."
        "Use the knowledge context to answer the question first.\n\n"
        "## Output requirements\n"
        "Return JSON only (single object), no explanations or Markdown:\n"
        '{"answer": "Your answer"}'
    )

    async def run(self, ctx: AgentContext) -> AnswerOutput:
        messages = ctx.messages().system(self.SYSTEM_PROMPT).user(ctx.query)
        output = await ctx.get_structured_output(messages)
        return output


async def main() -> None:
    def _env_bool(name: str) -> bool:
        return os.getenv(name, "").strip().lower() in {"1", "true", "yes", "y", "on"}

    llm_config = LLMConfig(
        provider=os.getenv("DATAPILLAR_LLM_PROVIDER", "openai"),
        api_key=os.getenv("DATAPILLAR_LLM_API_KEY"),
        model=os.getenv("DATAPILLAR_LLM_MODEL"),
        base_url=os.getenv("DATAPILLAR_LLM_BASE_URL"),
        enable_thinking=_env_bool("DATAPILLAR_LLM_ENABLE_THINKING"),
    )
    if not llm_config.is_configured():
        supported = ", ".join(Provider.list_supported())
        raise RuntimeError(
            "Please configure LLM first:\n"
            "  export DATAPILLAR_LLM_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_LLM_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_LLM_MODEL=\"gpt-4o\"\n"
            "Optional: export DATAPILLAR_LLM_BASE_URL=\"https://api.openai.com/v1\"\n"
            "Optional: export DATAPILLAR_LLM_ENABLE_THINKING=\"true\"\n"
            f"Supported providers: {supported}"
        )

    dimension_raw = os.getenv("DATAPILLAR_EMBEDDING_DIMENSION", "1536")
    try:
        dimension = int(dimension_raw)
    except ValueError as exc:
        raise RuntimeError("DATAPILLAR_EMBEDDING_DIMENSION must be an integer") from exc

    embedding_config = EmbeddingConfig(
        provider=os.getenv("DATAPILLAR_EMBEDDING_PROVIDER", "openai"),
        api_key=os.getenv("DATAPILLAR_EMBEDDING_API_KEY"),
        model=os.getenv("DATAPILLAR_EMBEDDING_MODEL"),
        base_url=os.getenv("DATAPILLAR_EMBEDDING_BASE_URL"),
        dimension=dimension,
    )
    if not embedding_config.is_configured():
        supported = ", ".join(EmbeddingBackend.list_supported())
        raise RuntimeError(
            "Please configure embedding first:\n"
            "  export DATAPILLAR_EMBEDDING_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_EMBEDDING_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_EMBEDDING_MODEL=\"text-embedding-3-small\"\n"
            "  export DATAPILLAR_EMBEDDING_DIMENSION=\"1536\"\n"
            "Optional: export DATAPILLAR_EMBEDDING_BASE_URL=\"https://api.openai.com/v1\"\n"
            f"Supported providers: {supported}"
        )

    namespace = "demo_knowledge_team"
    config = DatapillarConfig(
        llm=llm_config,
        embedding=embedding_config,
    )
    knowledge_config = KnowledgeConfig(
        namespaces=[namespace],
        embedding=embedding_config,
        vector_store={"type": "lance", "path": "./data/vectors"},
    )
    service = KnowledgeService(config=knowledge_config)
    await _prepare_demo_knowledge(service)
    await service.close()

    team = Datapillar(
        config=config,
        namespace=namespace,
        name="Knowledge Team",
        agents=[TeamAgent, SpecialistAgent],
        process=Process.SEQUENTIAL,
        knowledge=knowledge_config,
    )

    async for event in team.stream(query="What can Datapillar's knowledge framework do?", session_id="s1"):
        if event.get("event") == "agent.end":
            deliverable = event.get("data", {}).get("deliverable")
            if deliverable is not None:
                print(json.dumps(deliverable, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
