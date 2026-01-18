from __future__ import annotations

from types import SimpleNamespace

import pytest

import datapillar_oneagentic.storage as storage_module
from datapillar_oneagentic.storage.config import VectorStoreConfig


class _StubVectorStore:
    def __init__(self, **kwargs) -> None:
        self.kwargs = kwargs


class _StubKnowledgeStore:
    def __init__(self, *, vector_store, dimension: int, namespace: str) -> None:
        self.vector_store = vector_store
        self.dimension = dimension
        self.namespace = namespace


class _StubExperienceStore:
    def __init__(self, *, vector_store, dimension: int, namespace: str) -> None:
        self.vector_store = vector_store
        self.dimension = dimension
        self.namespace = namespace


def test_create_knowledge_store_lance(monkeypatch) -> None:
    monkeypatch.setattr(storage_module, "LanceVectorStore", lambda **kwargs: _StubVectorStore(**kwargs))
    monkeypatch.setattr(storage_module, "VectorKnowledgeStore", _StubKnowledgeStore)

    store = storage_module.create_knowledge_store(
        "ns1",
        vector_store_config=VectorStoreConfig(type="lance", path="/tmp/vectors"),
        embedding_config=SimpleNamespace(dimension=3),
    )

    assert store.dimension == 3
    assert store.vector_store.kwargs["namespace"] == "datapillar"
    assert store.vector_store.kwargs["path"] == "/tmp/vectors"
    assert store.namespace == "ns1"


def test_create_knowledge_store_chroma(monkeypatch) -> None:
    monkeypatch.setattr(storage_module, "ChromaVectorStore", lambda **kwargs: _StubVectorStore(**kwargs))
    monkeypatch.setattr(storage_module, "VectorKnowledgeStore", _StubKnowledgeStore)

    store = storage_module.create_knowledge_store(
        "ns2",
        vector_store_config=VectorStoreConfig(type="chroma", path="/tmp/chroma"),
        embedding_config=SimpleNamespace(dimension=8),
    )

    assert store.dimension == 8
    assert store.vector_store.kwargs["namespace"] == "datapillar"
    assert store.vector_store.kwargs["path"] == "/tmp/chroma"
    assert store.namespace == "ns2"


def test_create_knowledge_store_milvus(monkeypatch) -> None:
    monkeypatch.setattr(storage_module, "MilvusVectorStore", lambda **kwargs: _StubVectorStore(**kwargs))
    monkeypatch.setattr(storage_module, "VectorKnowledgeStore", _StubKnowledgeStore)

    store = storage_module.create_knowledge_store(
        "ns3",
        vector_store_config=VectorStoreConfig(type="milvus", uri="/tmp/milvus.db"),
        embedding_config=SimpleNamespace(dimension=12),
    )

    assert store.dimension == 12
    assert store.vector_store.kwargs["namespace"] == "datapillar"
    assert store.vector_store.kwargs["dim"] == 12
    assert store.namespace == "ns3"


def test_create_learning_store_uses_vector_store(monkeypatch) -> None:
    monkeypatch.setattr(storage_module, "LanceVectorStore", lambda **kwargs: _StubVectorStore(**kwargs))
    monkeypatch.setattr(storage_module, "VectorExperienceStore", _StubExperienceStore)

    store = storage_module.create_learning_store(
        "ns4",
        vector_store_config=VectorStoreConfig(type="lance", path="/tmp/exp"),
        embedding_config=SimpleNamespace(dimension=5),
    )

    assert store.dimension == 5
    assert store.vector_store.kwargs["namespace"] == "datapillar"
    assert store.namespace == "ns4"


def test_create_store_requires_dimension() -> None:
    with pytest.raises(ValueError):
        storage_module.create_knowledge_store(
            "ns5",
            vector_store_config=VectorStoreConfig(type="lance"),
            embedding_config=SimpleNamespace(dimension=None),
        )

    with pytest.raises(ValueError):
        storage_module.create_learning_store(
            "ns6",
            vector_store_config=VectorStoreConfig(type="lance"),
            embedding_config=SimpleNamespace(dimension=None),
        )
