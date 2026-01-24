"""
知识 RAG 快速使用示例：团队级知识入口 + Agent 级覆盖

运行命令：
    uv run python examples/quickstart_knowledge_team_agent.py

依赖安装：
    pip install datapillar-oneagentic[lance,knowledge]

配置要求：
    1) LLM（Datapillar 团队执行需要）
       export DATAPILLAR_LLM_PROVIDER="openai"              # openai | anthropic | glm | deepseek | openrouter | ollama
       export DATAPILLAR_LLM_API_KEY="sk-xxx"
       export DATAPILLAR_LLM_MODEL="gpt-4o"
       # 可选：export DATAPILLAR_LLM_BASE_URL="https://api.openai.com/v1"
       # 可选：export DATAPILLAR_LLM_ENABLE_THINKING="true"
    2) Embedding（知识入库与检索需要）
       export DATAPILLAR_EMBEDDING_PROVIDER="openai"        # openai | glm
       export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
       export DATAPILLAR_EMBEDDING_MODEL="text-embedding-3-small"
       export DATAPILLAR_EMBEDDING_DIMENSION="1536"
       # 可选：export DATAPILLAR_EMBEDDING_BASE_URL="https://api.openai.com/v1"

说明：
    - source_id 由系统生成，不需要传入。
    - content 可选；传了 content 就不会读取 source_uri 的内容。
    - source_uri 写本地完整路径且文件存在时，会自动读取文件内容；filename/mime_type 可选。
    - namespace 必须显式传入（由 Datapillar 与 ingest/retrieve 内部创建 store 绑定）。
    - 检索默认只在当前 namespace 内进行，跨 namespace 检索暂不支持。
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
        name="团队知识库",
        source_type="doc",
        source_uri="team.txt",
        content=(
            "Datapillar 是一个数据开发 SaaS 平台，提供知识切分与检索增强能力。\n"
            "知识框架是基座能力，可挂载到团队或 Agent。"
        ),
        filename="team.txt",
        metadata={"title": "团队知识示例"},
    )
    agent_source = KnowledgeSource(
        name="个人知识库",
        source_type="doc",
        source_uri="agent.txt",
        content="Agent 可以在团队知识之上叠加个人知识，并覆盖检索参数。",
        filename="agent.txt",
        metadata={"title": "个人知识示例"},
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
    name="团队知识使用者",
    deliverable_schema=AnswerOutput,
)
class TeamAgent:
    SYSTEM_PROMPT = (
        "你是团队知识使用者。"
        "如果知识上下文中包含答案，请优先引用并给出简洁回应。\n\n"
        "## 输出要求\n"
        "只能输出 JSON（单个对象），不得输出解释或 Markdown：\n"
        '{"answer": "你的结果"}'
    )

    async def run(self, ctx: AgentContext) -> AnswerOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        output = await ctx.get_structured_output(messages)
        return output


@agent(
    id="specialist_agent",
    name="知识特化 Agent",
    deliverable_schema=AnswerOutput,
    knowledge=Knowledge(
        sources=[
            KnowledgeSource(name="个人知识库", source_type="doc", source_uri="agent.txt"),
        ],
        retrieve=KnowledgeRetrieve(
            method="semantic",
            top_k=2,
        ),
    ),
)
class SpecialistAgent:
    SYSTEM_PROMPT = (
        "你是知识特化 Agent。"
        "优先使用知识上下文回答问题。\n\n"
        "## 输出要求\n"
        "只能输出 JSON（单个对象），不得输出解释或 Markdown：\n"
        '{"answer": "你的结果"}'
    )

    async def run(self, ctx: AgentContext) -> AnswerOutput:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        output = await ctx.get_structured_output(messages)
        return output


async def main() -> None:
    config = DatapillarConfig()
    if not config.llm.is_configured():
        supported = ", ".join(Provider.list_supported())
        raise RuntimeError(
            "请先配置 LLM：\n"
            "  export DATAPILLAR_LLM_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_LLM_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_LLM_MODEL=\"gpt-4o\"\n"
            "可选：export DATAPILLAR_LLM_BASE_URL=\"https://api.openai.com/v1\"\n"
            "可选：export DATAPILLAR_LLM_ENABLE_THINKING=\"true\"\n"
            f"支持 provider: {supported}"
        )
    if not config.embedding.is_configured():
        supported = ", ".join(EmbeddingBackend.list_supported())
        raise RuntimeError(
            "请先配置 Embedding：\n"
            "  export DATAPILLAR_EMBEDDING_PROVIDER=\"openai\"\n"
            "  export DATAPILLAR_EMBEDDING_API_KEY=\"sk-xxx\"\n"
            "  export DATAPILLAR_EMBEDDING_MODEL=\"text-embedding-3-small\"\n"
            "  export DATAPILLAR_EMBEDDING_DIMENSION=\"1536\"\n"
            "可选：export DATAPILLAR_EMBEDDING_BASE_URL=\"https://api.openai.com/v1\"\n"
            f"支持 provider: {supported}"
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

    # namespace 必填，用于知识隔离，需与 Datapillar 保持一致
    namespace = "demo_knowledge_team"
    await _prepare_demo_knowledge(namespace, knowledge_config)

    team = Datapillar(
        config=config,
        namespace=namespace,
        name="知识团队",
        agents=[TeamAgent, SpecialistAgent],
        process=Process.SEQUENTIAL,
        knowledge=Knowledge(
            sources=[KnowledgeSource(name="团队知识库", source_type="doc", source_uri="team.txt")],
            retrieve=KnowledgeRetrieve(
                method="semantic",
                top_k=4,
                inject=KnowledgeInject(mode="system", max_tokens=800),
            ),
        ),
    )

    async for event in team.stream(query="Datapillar 的知识框架能做什么？", session_id="s1"):
        if event.get("event") == "agent.end":
            deliverable = event.get("data", {}).get("deliverable")
            if deliverable is not None:
                print(json.dumps(deliverable, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
