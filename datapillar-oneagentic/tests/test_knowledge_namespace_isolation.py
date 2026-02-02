from __future__ import annotations

import importlib.util
import os
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
from datapillar_oneagentic.knowledge.identity import build_source_id
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
async def test_namespace_isolation() -> None:
    with tempfile.TemporaryDirectory() as tmpdir:
        vector_store_config = _select_vector(tmpdir)
        embedding_config = EmbeddingConfig(
            provider="openai",
            api_key="stub",
            model="stub",
            dimension=2,
        )

        store_a = create_knowledge_store(
            "ns_a",
            vector_store_config=vector_store_config,
            embedding_config=embedding_config,
        )
        store_b = create_knowledge_store(
            "ns_b",
            vector_store_config=vector_store_config,
            embedding_config=embedding_config,
        )
        await store_a.initialize()
        await store_b.initialize()

        embedder = _StubEmbedder()
        chunk_config = KnowledgeChunkConfig(
            mode="general",
            general={"max_tokens": 50, "overlap": 0},
        )
        ingestor_a = KnowledgeIngestor(
            store=store_a,
            embedding_provider=embedder,
        )
        ingestor_b = KnowledgeIngestor(
            store=store_b,
            embedding_provider=embedder,
        )

        file_path = os.path.join(tmpdir, "doc.txt")
        with open(file_path, "w", encoding="utf-8") as handle:
            handle.write("shared content for namespace isolation")

        source_a = KnowledgeSource(
            source=file_path,
            chunk=chunk_config,
            doc_uid="doc1",
            name="KB",
            source_type="doc",
        )
        source_b = KnowledgeSource(
            source=file_path,
            chunk=chunk_config,
            doc_uid="doc1",
            name="KB",
            source_type="doc",
        )

        await ingestor_a.ingest(sources=[source_a])
        await ingestor_b.ingest(sources=[source_b])

        expected_a = build_source_id(
            namespace="ns_a",
            source_type="doc",
            source_uri=source_a.source_uri,
            metadata=source_a.metadata,
        )
        expected_b = build_source_id(
            namespace="ns_b",
            source_type="doc",
            source_uri=source_b.source_uri,
            metadata=source_b.metadata,
        )

        assert source_a.source_id == expected_a
        assert source_b.source_id == expected_b

        retriever_a = KnowledgeRetriever(
            store=store_a,
            embedding_provider=embedder,
            retrieve_defaults=KnowledgeRetrieveConfig(
                method="semantic",
                top_k=3,
            ),
        )
        result_a = await retriever_a.retrieve(query="shared", knowledge=Knowledge(sources=[source_a]))
        assert result_a.hits
        assert {chunk.source_id for chunk, _ in result_a.hits} == {expected_a}

        retriever_b = KnowledgeRetriever(
            store=store_b,
            embedding_provider=embedder,
            retrieve_defaults=KnowledgeRetrieveConfig(
                method="semantic",
                top_k=3,
            ),
        )
        result_b = await retriever_b.retrieve(query="shared", knowledge=Knowledge(sources=[source_b]))
        assert result_b.hits
        assert {chunk.source_id for chunk, _ in result_b.hits} == {expected_b}

        await store_a.close()
        await store_b.close()
