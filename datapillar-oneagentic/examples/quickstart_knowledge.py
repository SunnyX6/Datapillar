"""
Knowledge RAG quickstart: document chunking + retrieval.

Run:
    uv run python examples/quickstart_knowledge.py

Dependencies:
    pip install datapillar-oneagentic[lance,knowledge]

Embedding configuration (choose one):
    1) Environment variables (recommended):
       export DATAPILLAR_EMBEDDING_PROVIDER="openai"          # openai | glm
       export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
       export DATAPILLAR_EMBEDDING_MODEL="text-embedding-3-small"
       export DATAPILLAR_EMBEDDING_DIMENSION="1536"
       # Optional: export DATAPILLAR_EMBEDDING_BASE_URL="https://api.openai.com/v1"
    2) Config file (see examples/config.example.toml)

Notes:
    - source_id is generated automatically.
    - content is optional; if provided, source_uri is not read.
    - If source_uri is a valid local path, content is read automatically; filename/mime_type are optional.
    - namespace must be provided explicitly (store is bound inside ingest/retrieve).
    - Retrieval is scoped to the current namespace; cross-namespace retrieval is not supported.
"""

import asyncio

from datapillar_oneagentic import DatapillarConfig
from datapillar_oneagentic.providers.llm import EmbeddingBackend

from datapillar_oneagentic.context import ContextBuilder
from datapillar_oneagentic.knowledge import (
    BM25SparseEmbedder,
    Knowledge,
    KnowledgeChunkConfig,
    KnowledgeConfig,
    KnowledgeRetrieveConfig,
    KnowledgeSource,
    KnowledgeRetriever,
)


async def main() -> None:
    config = DatapillarConfig()
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

    namespace = "demo_knowledge"
    knowledge_config = KnowledgeConfig(
        base_config={
            "embedding": embedding_config,
            "vector_store": {"type": "lance", "path": "./data/vectors"},
        },
        chunk_config=KnowledgeChunkConfig(
            mode="general",
            preprocess=["normalize_newlines", "remove_control", "collapse_whitespace"],
            general={"max_tokens": 80, "overlap": 10},
        ),
        retrieve_config=KnowledgeRetrieveConfig(
            method="hybrid",
            top_k=3,
        ),
    )

    sparse_embedder = BM25SparseEmbedder()

    sample_text = (
        "Datapillar is a data development SaaS platform covering ingestion, modeling, and quality governance.\n"
        "The knowledge framework provides parsing, preprocessing, chunking, vectorization, and retrieval augmentation.\n"
        "Retrieval supports semantic and hybrid recall, with rerank and dedupe before context injection.\n"
        "Namespace is a hard isolation boundary to prevent knowledge leakage across business lines.\n"
        "doc_id is generated deterministically; duplicate ingest triggers rebuild rather than duplication.\n"
        "Typical scenarios include data assistants, team knowledge bases, enterprise QA, and agent augmentation."
    )
    source = KnowledgeSource(
        name="Example knowledge base",
        source_type="doc",
        source_uri="demo.txt",
        content=sample_text,
        filename="demo.txt",
        metadata={"title": "Datapillar Overview"},
    )

    preview = source.chunk(chunk_config=knowledge_config.chunk_config)
    print(f"Chunk preview: chunks={len(preview.chunks)}, attachments={len(preview.attachments)}")
    if preview.chunks:
        print("First chunk preview:")
        print(preview.chunks[0].content)

    await source.ingest(
        namespace=namespace,
        config=knowledge_config,
        sparse_embedder=sparse_embedder,
    )

    retriever = KnowledgeRetriever.from_config(namespace=namespace, config=knowledge_config)
    knowledge = Knowledge(sources=[source], sparse_embedder=sparse_embedder)
    result = await retriever.retrieve(query="What are Datapillar's core capabilities?", knowledge=knowledge)
    inject_config = retriever.resolve_inject_config(knowledge)
    context = ContextBuilder.build_knowledge_context(
        chunks=[chunk for chunk, _ in result.hits],
        inject=inject_config,
    )
    print("\nRetrieval result:")
    print(context or "No relevant knowledge found.")
    await retriever.close()


if __name__ == "__main__":
    asyncio.run(main())
