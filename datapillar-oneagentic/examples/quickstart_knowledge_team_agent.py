"""
知识 RAG 快速使用示例：团队级知识入口 + Agent 级覆盖

运行命令：
    uv run python examples/quickstart_knowledge_team_agent.py

依赖安装：
    pip install datapillar-oneagentic[lance,knowledge]

配置要求：
    1) LLM（Datapillar 团队执行需要）
       export GLM_API_KEY="sk-xxx"
       export GLM_MODEL="glm-4.7"
       # 可选：export GLM_BASE_URL="https://open.bigmodel.cn/api/paas/v4"
       # 可选：export GLM_ENABLE_THINKING="true"
    2) Embedding（知识入库与检索需要）
       export GLM_EMBEDDING_API_KEY="sk-xxx"
       export GLM_EMBEDDING_MODEL="embedding-3"
       export GLM_EMBEDDING_DIMENSION="1024"
"""

import asyncio
import os

from pydantic import BaseModel

from datapillar_oneagentic import AgentContext, Datapillar, DatapillarConfig, Process, agent
from datapillar_oneagentic.knowledge import (
    DocumentInput,
    Knowledge,
    KnowledgeChunkConfig,
    KnowledgeInject,
    KnowledgeIngestor,
    KnowledgeRetrieve,
    KnowledgeSource,
)
from datapillar_oneagentic.providers.llm import EmbeddingProviderClient
from datapillar_oneagentic.storage import create_knowledge_store


# ============================================================================
# LLM 配置
# ============================================================================
LLM_PROVIDER = "glm"
LLM_API_KEY = os.environ.get("GLM_API_KEY")
LLM_BASE_URL = os.environ.get("GLM_BASE_URL")
LLM_MODEL = os.environ.get("GLM_MODEL")
LLM_ENABLE_THINKING = os.environ.get("GLM_ENABLE_THINKING", "false").lower() in {
    "1",
    "true",
    "yes",
}

if not LLM_API_KEY or not LLM_MODEL:
    raise RuntimeError("请设置 GLM_API_KEY 和 GLM_MODEL（可选 GLM_BASE_URL/GLM_ENABLE_THINKING）")

EMBEDDING_PROVIDER = "glm"
EMBEDDING_API_KEY = os.environ.get("GLM_EMBEDDING_API_KEY")
EMBEDDING_MODEL = os.environ.get("GLM_EMBEDDING_MODEL")
EMBEDDING_DIMENSION_RAW = os.environ.get("GLM_EMBEDDING_DIMENSION")

if not EMBEDDING_API_KEY or not EMBEDDING_MODEL or not EMBEDDING_DIMENSION_RAW:
    raise RuntimeError("请设置 GLM_EMBEDDING_API_KEY、GLM_EMBEDDING_MODEL、GLM_EMBEDDING_DIMENSION")

try:
    EMBEDDING_DIMENSION = int(EMBEDDING_DIMENSION_RAW)
except ValueError as exc:
    raise RuntimeError("GLM_EMBEDDING_DIMENSION 必须为整数") from exc


class AnswerOutput(BaseModel):
    answer: str


async def _prepare_demo_knowledge(namespace: str, config: DatapillarConfig) -> None:
    knowledge_store = create_knowledge_store(
        namespace,
        vector_store_config=config.vector_store,
        embedding_config=config.embedding,
    )
    await knowledge_store.initialize()

    embedding_provider = EmbeddingProviderClient(config.embedding)
    ingestor = KnowledgeIngestor(
        store=knowledge_store,
        embedding_provider=embedding_provider,
        config=KnowledgeChunkConfig(
            mode="general",
            general={"max_tokens": 200, "overlap": 40},
        ),
    )

    team_source = KnowledgeSource(
        source_id="kb_team",
        name="团队知识库",
        source_type="doc",
    )
    agent_source = KnowledgeSource(
        source_id="kb_agent",
        name="个人知识库",
        source_type="doc",
    )

    team_doc = DocumentInput(
        source=(
            "Datapillar 是一个数据开发 SaaS 平台，提供知识切分与检索增强能力。\n"
            "知识框架是基座能力，可挂载到团队或 Agent。"
        ),
        filename="team.txt",
        metadata={"title": "团队知识示例"},
    )
    agent_doc = DocumentInput(
        source="Agent 可以在团队知识之上叠加个人知识，并覆盖检索参数。",
        filename="agent.txt",
        metadata={"title": "个人知识示例"},
    )

    await ingestor.ingest(source=team_source, documents=[team_doc])
    await ingestor.ingest(source=agent_source, documents=[agent_doc])


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
            KnowledgeSource(source_id="kb_agent", name="个人知识库", source_type="doc"),
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
    llm_config = {
        "provider": LLM_PROVIDER,
        "api_key": LLM_API_KEY,
        "model": LLM_MODEL,
        "enable_thinking": LLM_ENABLE_THINKING,
        "timeout_seconds": 120,
        "retry": {"max_retries": 2},
    }
    if LLM_BASE_URL:
        llm_config["base_url"] = LLM_BASE_URL

    embedding_config = {
        "provider": EMBEDDING_PROVIDER,
        "api_key": EMBEDDING_API_KEY,
        "model": EMBEDDING_MODEL,
        "dimension": EMBEDDING_DIMENSION,
    }
    config = DatapillarConfig(llm=llm_config, embedding=embedding_config)

    namespace = "demo_knowledge_team"
    await _prepare_demo_knowledge(namespace, config)

    team = Datapillar(
        config=config,
        namespace=namespace,
        name="知识团队",
        agents=[TeamAgent, SpecialistAgent],
        process=Process.SEQUENTIAL,
        knowledge=Knowledge(
            sources=[KnowledgeSource(source_id="kb_team", name="团队知识库", source_type="doc")],
            retrieve=KnowledgeRetrieve(
                method="semantic",
                top_k=4,
                inject=KnowledgeInject(mode="system", max_tokens=800),
            ),
        ),
    )

    async for event in team.stream(query="Datapillar 的知识框架能做什么？", session_id="s1"):
        if event.get("event") == "result":
            print(event["result"]["deliverable"])


if __name__ == "__main__":
    asyncio.run(main())
