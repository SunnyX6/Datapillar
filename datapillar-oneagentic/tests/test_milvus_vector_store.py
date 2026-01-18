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

    def add_field(self, *args, **kwargs) -> None:
        self.fields.append((args, kwargs))


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


class _FakeAsyncMilvusClient:
    def __init__(self, *, has_collection: bool = False, create_error: Exception | None = None) -> None:
        self._has_collection = has_collection
        self._create_error = create_error
        self.has_collection_calls = 0
        self.create_calls = 0

    async def has_collection(self, _name: str) -> bool:
        self.has_collection_calls += 1
        return self._has_collection

    async def create_collection(self, **_kwargs) -> None:
        self.create_calls += 1
        if self._create_error:
            raise self._create_error


def _install_fake_pymilvus(monkeypatch) -> None:
    fake_module = types.SimpleNamespace(
        DataType=_FakeDataType,
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


@pytest.mark.asyncio
async def test_milvus_ensure_collection_create_success(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient()
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        namespace="datapillar",
        dim=2,
    )
    store._client = client

    await store.ensure_collection(_build_schema())

    assert client.has_collection_calls == 1
    assert client.create_calls == 1


@pytest.mark.asyncio
async def test_milvus_ensure_collection_ignores_existing(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient(create_error=RuntimeError("collection already exists"))
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        namespace="datapillar",
        dim=2,
    )
    store._client = client

    await store.ensure_collection(_build_schema())

    assert client.create_calls == 1


@pytest.mark.asyncio
async def test_milvus_ensure_collection_raises_on_unknown_error(monkeypatch) -> None:
    _install_fake_pymilvus(monkeypatch)
    client = _FakeAsyncMilvusClient(create_error=RuntimeError("boom"))
    store = MilvusVectorStore(
        uri="http://example.com",
        token=None,
        namespace="datapillar",
        dim=2,
    )
    store._client = client

    with pytest.raises(RuntimeError, match="boom"):
        await store.ensure_collection(_build_schema())
