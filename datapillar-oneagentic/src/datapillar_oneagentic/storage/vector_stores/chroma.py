"""
Chroma VectorStore 实现
"""

from __future__ import annotations

import json
import logging
from typing import Any

from datapillar_oneagentic.storage.vector_stores.base import (
    VectorCollectionSchema,
    VectorFieldType,
    VectorSearchResult,
    VectorStore,
    VectorStoreCapabilities,
)

logger = logging.getLogger(__name__)


class ChromaVectorStore(VectorStore):
    """Chroma VectorStore"""

    def __init__(
        self,
        *,
        path: str | None,
        host: str | None,
        port: int,
        namespace: str,
    ) -> None:
        super().__init__(namespace=namespace)
        self._path = path
        self._host = host
        self._port = port
        self._client = None
        self._collections: dict[str, Any] = {}

    @property
    def capabilities(self) -> VectorStoreCapabilities:
        return VectorStoreCapabilities(supports_dense=True, supports_sparse=True, supports_filter=True)

    async def initialize(self) -> None:
        import chromadb

        if self._host:
            self._client = chromadb.HttpClient(host=self._host, port=self._port)
            logger.info(f"初始化 ChromaVectorStore (远程): {self._host}:{self._port}")
        else:
            self._client = chromadb.PersistentClient(path=self._path or "./data/chroma")
            logger.info(f"初始化 ChromaVectorStore (本地): {self._path}")

    async def close(self) -> None:
        self._client = None
        self._collections.clear()
        logger.info("ChromaVectorStore 已关闭")

    async def ensure_collection(self, schema: VectorCollectionSchema) -> None:
        if self._client is None:
            await self.initialize()

        if schema.name in self._collections:
            return

        name = self._namespaced(schema.name)
        collection = self._client.get_or_create_collection(name=name)
        self._collections[schema.name] = collection

    async def add(self, collection: str, records: list[dict[str, Any]]) -> None:
        if not records:
            return
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        col = self._collections[collection]

        ids: list[str] = []
        embeddings: list[list[float]] = []
        metadatas: list[dict[str, Any]] = []
        documents: list[str] = []

        for record in records:
            record_id = str(record.get(schema.primary_key, ""))
            if not record_id:
                raise ValueError(f"记录缺少主键字段: {schema.primary_key}")
            embedding = record.get("vector")
            if embedding is None:
                raise ValueError("Chroma 需要 vector 字段")

            metadata, document = _split_record(record)
            ids.append(record_id)
            embeddings.append(list(embedding))
            metadatas.append(metadata)
            documents.append(document)

        col.add(ids=ids, embeddings=embeddings, metadatas=metadatas, documents=documents)

    async def get(self, collection: str, ids: list[str]) -> list[dict[str, Any]]:
        if not ids:
            return []
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        col = self._collections[collection]

        result = col.get(ids=ids, include=["metadatas", "documents", "ids"])
        return _merge_records(result)

    async def delete(self, collection: str, ids: list[str]) -> int:
        if not ids:
            return 0
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        col = self._collections[collection]
        col.delete(ids=ids)
        return len(ids)

    async def search(
        self,
        collection: str,
        query_vector: list[float],
        k: int = 5,
        filters: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        col = self._collections[collection]

        result = col.query(
            query_embeddings=[query_vector],
            n_results=k,
            where=filters,
            include=["metadatas", "documents", "distances"],
        )
        return _merge_search_results(result)

    async def query(
        self,
        collection: str,
        filters: dict[str, Any] | None = None,
        limit: int | None = None,
    ) -> list[dict[str, Any]]:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        col = self._collections[collection]

        result = col.get(where=filters, limit=limit, include=["metadatas", "documents", "ids"])
        return _merge_records(result)

    async def count(self, collection: str) -> int:
        schema = self.get_schema(collection)
        await self.ensure_collection(schema)
        col = self._collections[collection]
        return col.count()


def _split_record(record: dict[str, Any]) -> tuple[dict[str, Any], str]:
    metadata = {}
    document = ""
    for key, value in record.items():
        if key == "vector":
            continue
        if key == "content":
            document = str(value)
            metadata[key] = document
            continue
        if key == "sparse_vector":
            metadata[key] = json.dumps(value, ensure_ascii=False)
            continue
        if isinstance(value, (dict, list)):
            metadata[key] = json.dumps(value, ensure_ascii=False)
            continue
        metadata[key] = value
    return metadata, document


def _merge_records(result: dict[str, Any]) -> list[dict[str, Any]]:
    ids = result.get("ids", [[]])
    metadatas = result.get("metadatas", [[]])
    documents = result.get("documents", [[]])

    records = []
    for idx, record_id in enumerate(ids[0] if ids else []):
        metadata = metadatas[0][idx] if metadatas and metadatas[0] else {}
        document = documents[0][idx] if documents and documents[0] else ""
        record = dict(metadata)
        if record_id and "id" not in record:
            record["id"] = record_id
        if "content" not in record and document:
            record["content"] = document
        if "sparse_vector" in record and isinstance(record["sparse_vector"], str):
            try:
                record["sparse_vector"] = json.loads(record["sparse_vector"])
            except json.JSONDecodeError:
                pass
        records.append(record)
    return records


def _merge_search_results(result: dict[str, Any]) -> list[VectorSearchResult]:
    records = _merge_records(result)
    distances = result.get("distances", [[]])
    if not distances or distances[0] is None:
        raise ValueError("Chroma 搜索结果缺少 distances")
    if len(distances[0]) != len(records):
        raise ValueError("Chroma 搜索结果 distances 数量不一致")
    scored: list[VectorSearchResult] = []
    for idx, record in enumerate(records):
        distance = distances[0][idx] if distances[0] else None
        if distance is None:
            raise ValueError("Chroma 搜索结果缺少 distance")
        scored.append(
            VectorSearchResult(
                record=record,
                score=float(distance),
                score_kind="distance",
            )
        )
    return scored
