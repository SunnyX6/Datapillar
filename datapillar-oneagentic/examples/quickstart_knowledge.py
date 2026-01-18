"""
知识 RAG 快速使用示例：文档切分 + 检索

运行命令：
    uv run python examples/quickstart_knowledge.py

依赖安装：
    pip install datapillar-oneagentic[lance,knowledge]

Embedding 配置（二选一）：
    1) 环境变量（推荐）：
       export GLM_EMBEDDING_API_KEY="sk-xxx"
       export GLM_EMBEDDING_MODEL="embedding-3"
       export GLM_EMBEDDING_DIMENSION="1024"
    2) 配置文件（参考 examples/config.example.toml）
"""

import asyncio
import os

from datapillar_oneagentic import DatapillarConfig
from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.knowledge import (
    DocumentInput,
    Knowledge,
    KnowledgeChunkConfig,
    KnowledgeConfig,
    KnowledgeIngestor,
    KnowledgeRetrieveConfig,
    KnowledgeSource,
    KnowledgeRetriever,
)
from datapillar_oneagentic.providers.llm import EmbeddingProviderClient
from datapillar_oneagentic.storage import create_knowledge_store


async def main() -> None:
    embedding_api_key = os.environ.get("GLM_EMBEDDING_API_KEY")
    embedding_model = os.environ.get("GLM_EMBEDDING_MODEL")
    embedding_dimension_raw = os.environ.get("GLM_EMBEDDING_DIMENSION")

    if not embedding_api_key or not embedding_model or not embedding_dimension_raw:
        raise RuntimeError("请先配置 GLM_EMBEDDING_API_KEY、GLM_EMBEDDING_MODEL、GLM_EMBEDDING_DIMENSION")

    try:
        embedding_dimension = int(embedding_dimension_raw)
    except ValueError as exc:
        raise RuntimeError("GLM_EMBEDDING_DIMENSION 必须为整数") from exc

    embedding_config = {
        "provider": "glm",
        "api_key": embedding_api_key,
        "model": embedding_model,
        "dimension": embedding_dimension,
    }
    config = DatapillarConfig(embedding=embedding_config)

    namespace = "demo_knowledge"
    knowledge_store = create_knowledge_store(
        namespace,
        vector_store_config=config.vector_store,
        embedding_config=config.embedding,
    )
    await knowledge_store.initialize()
    embedding_provider = EmbeddingProviderClient(config.embedding)

    source = KnowledgeSource(
        source_id="kb_demo",
        name="示例知识库",
        source_type="doc",
    )
    sample_text = (
        "Datapillar 是一个数据开发 SaaS 平台。\n"
        "它提供知识切分、检索增强、经验学习等能力。\n"
        "知识检索支持 rerank、证据分组与去重。\n"
        "使用场景包括数据开发助手、团队知识库与 Agent 增强。"
    )
    doc = DocumentInput(
        source=sample_text,
        filename="demo.txt",
        metadata={"title": "Datapillar 介绍"},
    )

    chunk_config = KnowledgeChunkConfig(
        mode="general",
        preprocess=["normalize_newlines", "remove_control", "collapse_whitespace"],
        general={"max_tokens": 200, "overlap": 40},
    )
    ingestor = KnowledgeIngestor(
        store=knowledge_store,
        embedding_provider=embedding_provider,
        config=chunk_config,
    )

    previews = ingestor.preview(documents=[doc])
    preview = previews[0]
    print(f"切分预览：chunks={len(preview.chunks)}, attachments={len(preview.attachments)}")
    if preview.chunks:
        print("首个分片预览：")
        print(preview.chunks[0].content)

    await ingestor.ingest(source=source, documents=[doc])

    retriever = KnowledgeRetriever(
        store=knowledge_store,
        embedding_provider=embedding_provider,
        config=KnowledgeConfig(
            retrieve=KnowledgeRetrieveConfig(
                method="semantic",
                top_k=3,
            )
        ),
    )
    knowledge = Knowledge(sources=[source])
    result = await retriever.retrieve(query="Datapillar 的核心能力是什么？", knowledge=knowledge)
    inject_config = retriever.resolve_inject_config(knowledge)
    context = ContextBuilder.build_knowledge_context(
        chunks=[chunk for chunk, _ in result.hits],
        inject=inject_config,
    )
    print("\n检索结果：")
    print(context or "未找到相关知识。")


if __name__ == "__main__":
    asyncio.run(main())
