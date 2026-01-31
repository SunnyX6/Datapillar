from __future__ import annotations

import importlib.util
import tempfile

import pytest

from datapillar_oneagentic.knowledge import (
    Knowledge,
    KnowledgeChunkConfig,
    KnowledgeIngestor,
    KnowledgeRetrieveConfig,
    KnowledgeSource,
)
from datapillar_oneagentic.knowledge.retriever import KnowledgeRetriever
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage import create_knowledge_store
from datapillar_oneagentic.storage.config import VectorStoreConfig


class _StubEmbedder:
    async def embed_text(self, text: str) -> list[float]:
        return [float(len(text)), 0.0]

    async def embed_texts(self, texts: list[str]) -> list[list[float]]:
        return [[float(len(text)), 0.0] for text in texts]


def _module_available(module_name: str) -> bool:
    return importlib.util.find_spec(module_name) is not None


def _select_vector(path: str) -> VectorStoreConfig:
    if _module_available("lancedb") and _module_available("pyarrow"):
        return VectorStoreConfig(type="lance", path=path)
    if _module_available("chromadb"):
        return VectorStoreConfig(type="chroma", path=path)
    pytest.skip("vector_store backend (lancedb/pyarrow or chromadb) is not available")


@pytest.mark.asyncio
async def test_knowledge_ingest() -> None:
    with tempfile.TemporaryDirectory() as tmpdir:
        vector_store_config = _select_vector(tmpdir)
        embedding_config = EmbeddingConfig(
            provider="openai",
            api_key="stub",
            model="stub",
            dimension=2,
        )

        store = create_knowledge_store(
            "ns_integration",
            vector_store_config=vector_store_config,
            embedding_config=embedding_config,
        )
        await store.initialize()

        embedder = _StubEmbedder()
        chunk_config = KnowledgeChunkConfig(
            mode="general",
            general={"max_tokens": 50, "overlap": 0},
        )
        ingestor = KnowledgeIngestor(
            store=store,
            embedding_provider=embedder,
        )
        source = KnowledgeSource(
            source="Datapillar knowledge integration test text.",
            chunk=chunk_config,
            name="KB",
            source_type="doc",
            filename="doc.txt",
        )
        await ingestor.ingest(sources=[source])

        retriever = KnowledgeRetriever(
            store=store,
            embedding_provider=embedder,
            retrieve_defaults=KnowledgeRetrieveConfig(
                method="semantic",
                top_k=1,
            ),
        )
        knowledge = Knowledge(sources=[source])
        result = await retriever.retrieve(query="Datapillar", knowledge=knowledge)

        await store.close()

        assert result.hits
