# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Knowledge RAG quickstart: document chunking + retrieval.

Run:
    uv run python examples/quickstart_knowledge.py

Dependencies:
    pip install datapillar-oneagentic[lance,knowledge]

Embedding configuration (environment variables):
    export DATAPILLAR_EMBEDDING_PROVIDER="openai"          # openai | glm
    export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
    export DATAPILLAR_EMBEDDING_MODEL="text-embedding-3-small"
    export DATAPILLAR_EMBEDDING_DIMENSION="1536"
    # Optional: export DATAPILLAR_EMBEDDING_BASE_URL="https://api.openai.com/v1"

Notes:
    - source_id is generated automatically.
    - source accepts a file path, URL, raw text, or bytes.
    - Each document must provide its own chunk config.
    - namespace must be provided explicitly (store is bound inside ingest/retrieve).
    - Retrieval is scoped to the current namespace; cross-namespace retrieval is not supported.
"""

import asyncio
import os

from datapillar_oneagentic.providers.llm import EmbeddingBackend
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig

from datapillar_oneagentic.knowledge import (
    BM25SparseEmbedder,
    Knowledge,
    KnowledgeConfig,
    KnowledgeChunkRequest,
    KnowledgeRetrieve,
    KnowledgeSource,
    KnowledgeService,
)


async def main() -> None:
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

    namespace = "demo_knowledge"
    knowledge_config = KnowledgeConfig(
        namespaces=[namespace],
        embedding=embedding_config,
        vector_store={"type": "lance", "path": "./data/vectors"},
    )

    sparse_embedder = BM25SparseEmbedder()

    sample_text = (
        "Datapillar is a data development SaaS platform covering ingestion, modeling, and quality governance.\n"
        "The knowledge framework provides parsing, preprocessing, chunking, vectorization, and retrieval augmentation.\n"
        "Retrieval supports semantic and hybrid recall, with rerank and dedupe before context injection.\n"
        "Namespace is a hard isolation boundary to prevent knowledge leakage across business lines.\n"
        "doc_id is generated per ingest; duplicate files are stored as separate documents by default.\n"
        "Typical scenarios include data assistants, team knowledge bases, enterprise QA, and agent augmentation."
    )
    chunk_config = {
        "mode": "general",
        "preprocess": ["normalize_newlines", "remove_control", "collapse_whitespace"],
        "general": {"max_tokens": 80, "overlap": 10},
    }
    source = KnowledgeSource(
        source=sample_text,
        chunk=chunk_config,
        doc_uid="demo-doc",
        name="Example knowledge base",
        source_type="doc",
        filename="demo.txt",
        metadata={"title": "Datapillar Overview"},
    )

    service = KnowledgeService(config=knowledge_config)
    previews = await service.chunk(
        KnowledgeChunkRequest(
            sources=[source],
            preview=True,
        ),
        namespace=namespace,
    )
    preview = previews[0] if previews else None
    if preview:
        print(f"Chunk preview: chunks={len(preview.chunks)}, attachments={len(preview.attachments)}")
        if preview.chunks:
            print("First chunk preview:")
            print(preview.chunks[0].content)

    await service.chunk(
        KnowledgeChunkRequest(
            sources=[source],
            sparse_embedder=sparse_embedder,
        ),
        namespace=namespace,
    )

    knowledge = Knowledge(sources=[source], sparse_embedder=sparse_embedder)
    result = await service.retrieve(
        query="What are Datapillar's core capabilities?",
        namespaces=[namespace],
        knowledge=knowledge,
        retrieve=KnowledgeRetrieve(method="semantic", top_k=3),
    )
    context = "\n\n".join([chunk.content for chunk, _ in result.hits])
    print("\nRetrieval result:")
    print(context or "No relevant knowledge found.")
    await service.close()


if __name__ == "__main__":
    asyncio.run(main())
