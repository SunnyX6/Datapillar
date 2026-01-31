from __future__ import annotations

import importlib.util
import tempfile

import pytest

from datapillar_oneagentic.knowledge import (
    KnowledgeChunkConfig,
    KnowledgeChunkEdit,
    KnowledgeChunkRequest,
    KnowledgeConfig,
    KnowledgeService,
    KnowledgeSource,
)
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig


class _StubEmbeddingProvider:
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


def _build_service(tmpdir: str) -> KnowledgeService:
    vector_store_config = _select_vector(tmpdir)
    embedding_config = EmbeddingConfig(
        provider="openai",
        api_key="stub",
        model="stub",
        dimension=2,
    )
    knowledge_config = KnowledgeConfig(
        namespaces=["ns_chunk_edit"],
        embedding=embedding_config,
        vector_store=vector_store_config,
    )
    service = KnowledgeService(config=knowledge_config)
    service._runtime.embedding_provider = _StubEmbeddingProvider()
    return service


@pytest.mark.asyncio
async def test_list_and_upsert_chunks() -> None:
    with tempfile.TemporaryDirectory() as tmpdir:
        service = _build_service(tmpdir)
        chunk_config = KnowledgeChunkConfig(
            mode="general",
            general={"max_tokens": 5, "overlap": 0},
        )
        source = KnowledgeSource(
            source="alpha beta gamma",
            chunk=chunk_config,
            name="demo",
            source_type="doc",
        )
        await service.chunk(KnowledgeChunkRequest(sources=[source]), namespace="ns_chunk_edit")

        chunks = await service.list_chunks(namespace="ns_chunk_edit")
        assert chunks
        target = chunks[0]

        await service.upsert_chunks(
            chunks=[KnowledgeChunkEdit(chunk_id=target.chunk_id, content="new content")],
            namespace="ns_chunk_edit",
        )
        updated = await service.list_chunks(filters={"chunk_id": target.chunk_id}, namespace="ns_chunk_edit")
        assert updated
        assert updated[0].content == "new content"

        await service.close()


@pytest.mark.asyncio
async def test_delete_parent_cascades() -> None:
    with tempfile.TemporaryDirectory() as tmpdir:
        service = _build_service(tmpdir)
        chunk_config = KnowledgeChunkConfig(
            mode="parent_child",
            parent_child={
                "parent": {"max_tokens": 6, "overlap": 0},
                "child": {"max_tokens": 3, "overlap": 0},
            },
        )
        source = KnowledgeSource(
            source="abcdefghijklmno",
            chunk=chunk_config,
            name="demo",
            source_type="doc",
        )
        await service.chunk(KnowledgeChunkRequest(sources=[source]), namespace="ns_chunk_edit")

        chunks = await service.list_chunks(namespace="ns_chunk_edit")
        parent = next(item for item in chunks if item.chunk_type == "parent")
        child_ids = [item.chunk_id for item in chunks if item.parent_id == parent.chunk_id]
        assert child_ids

        await service.delete_chunks(chunk_ids=[parent.chunk_id], namespace="ns_chunk_edit")
        remaining = await service.list_chunks(filters={"doc_id": parent.doc_id}, namespace="ns_chunk_edit")
        assert not remaining
        doc = await service._runtime.store.get_doc(parent.doc_id)
        assert doc is None

        await service.close()
