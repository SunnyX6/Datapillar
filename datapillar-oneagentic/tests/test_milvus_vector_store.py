from __future__ import annotations

import sys
import types

import pytest

from datapillar_oneagentic.storage.vector_stores.base import (
    VectorCollectionSchema,
    VectorField,
    VectorFieldType,
)
from datapillar_oneagentic.storage.vector_stores.milvus import MilvusVectorStore


class _FakeSchema:
    def __init__(self) -> None:
        self.fields = []
        self.functions = []

    def add_field(self, *args, **kwargs) -> None:
        self.fields.append((args, kwargs))

    def add_function(self, *args, **kwargs) -> None:
        self.functions.append((args, kwargs))


class _FakeIndexParams:
    def __init__(self) -> None:
        self.indexes = []

    def add_index(self, **kwargs) -> None:
        self.indexes.append(kwargs)


class _FakeMilvusClient:
    @staticmethod
    def create_schema(auto_id: bool = False, enable_dynamic_field: bool = False) -> _FakeSchema:
        return _FakeSchema()

    @staticmethod
    def prepare_index_params() -> _FakeIndexParams:
        return _FakeIndexParams()


class _FakeDataType:
    VARCHAR = "VARCHAR"
    INT64 = "INT64"
    FLOAT = "FLOAT"
    FLOAT_VECTOR = "FLOAT_VECTOR"
    SPARSE_FLOAT_VECTOR = "SPARSE_FLOAT_VECTOR"


class _FakeFunctionType:
    BM25 = "BM25"


class _FakeFunction:
    def __init__(self, **kwargs) -> None:
        self.kwargs = kwargs


class _FakeAsyncMilvusClient:
    def __init__(self, *, has_collection: bool = False, create_error: Exception | None = None) -> None:
        self._has_collection = has_collection
        self._create_error = create_error
        self.has_collection_calls = 0
        self.create_calls = 0
        self.create_kwargs = None
        self.search_calls = 0
        self.search_kwargs = None

    async def has_collection(self, _name: str) -> bool:
        self.has_collection_calls += 1
        return self._has_collection

    async def create_collection(self, **_kwargs) -> None:
        self.create_calls += 1
        self.create_kwargs = dict(_kwargs)
        if self._create_error:
            raise self._create_error

    async def search(self, **_kwargs):
        self.search_calls += 1
        self.search_kwargs = dict(_kwargs)
        return [[{"id": "row1", "score": 0.9, "entity": {"chunk_id": "doc1:0"}}]]


def _install_fake_pymilvus(monkeypatch) -> None:
    fake_module = types.SimpleNamespace(
        DataType=_FakeDataType,
        Function=_FakeFunction,
        FunctionType=_FakeFunctionType,
        MilvusClient=_FakeMilvusClient,
    )
    monkeypatch.setitem(sys.modules, "pymilvus", fake_module)


def _build_schema() -> VectorCollectionSchema:
    return VectorCollectionSchema(
        name="knowledge_chunks",
        primary_key="chunk_key",
        fields=[
            VectorField("chunk_key", VectorFieldType.STRING),
            VectorField("vector", VectorFieldType.VECTOR, dimension=2),
        ],
    )


def _build_bm25_schema() -> VectorCollectionSchema:
    return VectorCollectionSchema(
        name="knowledge_chunks",
        primary_key="chunk_key",
        fields=[
            VectorField("chunk_key", VectorFieldType.STRING),
            VectorField("content", VectorFieldType.STRING),
            VectorField("sparse_vector", VectorFieldType.SPARSE_VECTOR),
            VectorField("vector", VectorFieldType.VECTOR, dimension=2),
        ],
    )


@pytest.mark.asyncio
async def test_ensure_collection(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient()
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        user=None,
        password=None,
        db_name=None,
        namespace="datapillar",
        dim=2,
    )
    store._client = client

    await store.ensure_collection(_build_schema())

    assert client.has_collection_calls == 1
    assert client.create_calls == 1


@pytest.mark.asyncio
async def test_ensure_collection2(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient(create_error=RuntimeError("collection already exists"))
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        user=None,
        password=None,
        db_name=None,
        namespace="datapillar",
        dim=2,
    )
    store._client = client

    await store.ensure_collection(_build_schema())

    assert client.create_calls == 1


@pytest.mark.asyncio
async def test_ensure_collection3(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient(create_error=RuntimeError("boom"))
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        user=None,
        password=None,
        db_name=None,
        namespace="datapillar",
        dim=2,
    )
    store._client = client

    with pytest.raises(RuntimeError, match="boom"):
        await store.ensure_collection(_build_schema())


@pytest.mark.asyncio
async def test_custom_index_params(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient()
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        user=None,
        password=None,
        db_name=None,
        namespace="datapillar",
        dim=2,
        index_params={"index_type": "HNSW", "metric_type": "COSINE", "params": {"M": 8}},
    )
    store._client = client

    await store.ensure_collection(_build_schema())

    index_params = client.create_kwargs["index_params"]
    assert index_params.indexes
    assert index_params.indexes[0]["index_type"] == "HNSW"
    assert index_params.indexes[0]["metric_type"] == "COSINE"
    assert index_params.indexes[0]["params"] == {"M": 8}


@pytest.mark.asyncio
async def test_full_text_search_requires_bm25(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient()
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        user=None,
        password=None,
        db_name=None,
        namespace="datapillar",
        dim=2,
    )
    store._client = client

    store.register_schema(_build_schema())
    with pytest.raises(ValueError, match="BM25"):
        await store.full_text_search(_build_schema().name, query_text="hello", k=1)


@pytest.mark.asyncio
async def test_full_text_search_ok(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient()
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        user=None,
        password=None,
        db_name=None,
        namespace="datapillar",
        dim=2,
    )
    store._client = client

    store.register_schema(_build_bm25_schema())
    await store.ensure_collection(_build_bm25_schema())
    results = await store.full_text_search("knowledge_chunks", query_text="milvus", k=1)

    assert client.search_calls == 1
    assert results
    assert results[0].record["chunk_key"] == "row1"
