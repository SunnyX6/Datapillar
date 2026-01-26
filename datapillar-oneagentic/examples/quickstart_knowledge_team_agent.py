"""
Knowledge RAG quickstart: team-level entry + agent-level override.

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
    - content is optional; if provided, source_uri is not read.
    - If source_uri is a valid local path, content is read automatically; filename/mime_type are optional.
    - namespace must be provided explicitly (store is bound inside Datapillar and ingest/retrieve).
    - Retrieval is scoped to the current namespace; cross-namespace retrieval is not supported.
"""

import asyncio
import json

from pydantic import BaseModel

from datapillar_oneagentic import AgentContext, Datapillar, DatapillarConfig, Process, agent
from datapillar_oneagentic.providers.llm import EmbeddingBackend, Provider
from datapillar_oneagentic.knowledge import (
    Knowledge,
    KnowledgeChunkConfig,
    KnowledgeConfig,
    KnowledgeInject,
    KnowledgeInjectConfig,
    KnowledgeRetrieve,
    KnowledgeRetrieveConfig,
    KnowledgeSource,
)


class AnswerOutput(BaseModel):
    answer: str


async def _prepare_demo_knowledge(namespace: str, knowledge_config: KnowledgeConfig) -> None:

    team_source = KnowledgeSource(
        name="Team knowledge base",
        source_type="doc",
        source_uri="team.txt",
        content=(
            "Datapillar is a data development SaaS platform with knowledge chunking and retrieval augmentation.\n"
            "The knowledge framework is a foundational capability that can be attached to teams or agents."
        ),
        filename="team.txt",
        metadata={"title": "Team knowledge example"},
    )
    agent_source = KnowledgeSource(
        name="Personal knowledge base",
        source_type="doc",
        source_uri="agent.txt",
        content="Agents can layer personal knowledge on top of team knowledge and override retrieval settings.",
        filename="agent.txt",
        metadata={"title": "Personal knowledge example"},
    )

    await team_source.ingest(
        namespace=namespace,
        config=knowledge_config,
    )
    await agent_source.ingest(
        namespace=namespace,
        config=knowledge_config,
    )


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
    knowledge=Knowledge(
        sources=[
            KnowledgeSource(name="Personal knowledge base", source_type="doc", source_uri="agent.txt"),
        ],
        retrieve=KnowledgeRetrieve(
            method="semantic",
            top_k=2,
        ),
    ),
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
    config = DatapillarConfig()
    if not config.llm.is_configured():
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
    if not config.embedding.is_configured():
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

    embedding_config = config.embedding.model_dump()
    knowledge_config = KnowledgeConfig(
        base_config={
            "embedding": embedding_config,
            "vector_store": {"type": "lance", "path": "./data/vectors"},
        },
        chunk_config=KnowledgeChunkConfig(
            mode="general",
            general={"max_tokens": 200, "overlap": 40},
        ),
        retrieve_config=KnowledgeRetrieveConfig(
            method="semantic",
            top_k=4,
            inject=KnowledgeInjectConfig(mode="system", max_tokens=800),
        ),
    )
    config.knowledge = knowledge_config

    # namespace is required and must match Datapillar for knowledge isolation.
    namespace = "demo_knowledge_team"
    await _prepare_demo_knowledge(namespace, knowledge_config)

    team = Datapillar(
        config=config,
        namespace=namespace,
        name="Knowledge Team",
        agents=[TeamAgent, SpecialistAgent],
        process=Process.SEQUENTIAL,
        knowledge=Knowledge(
            sources=[KnowledgeSource(name="Team knowledge base", source_type="doc", source_uri="team.txt")],
            retrieve=KnowledgeRetrieve(
                method="semantic",
                top_k=4,
                inject=KnowledgeInject(mode="system", max_tokens=800),
            ),
        ),
    )

    async for event in team.stream(query="What can Datapillar's knowledge framework do?", session_id="s1"):
        if event.get("event") == "agent.end":
            deliverable = event.get("data", {}).get("deliverable")
            if deliverable is not None:
                print(json.dumps(deliverable, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
