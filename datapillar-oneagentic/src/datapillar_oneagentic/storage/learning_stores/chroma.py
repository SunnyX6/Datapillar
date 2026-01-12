"""
ChromaVectorStore - Chroma 向量存储

支持本地和远程两种模式：
- 本地模式：使用 PersistentClient，嵌入式存储
- 远程模式：使用 AsyncHttpClient，连接 Chroma Server

依赖：pip install chromadb

使用示例：
```python
from datapillar_oneagentic.storage.learning_stores import (
    LearningStore,
    ChromaVectorStore,
)

# 本地模式
vector_store = ChromaVectorStore(path="./data/chroma")

# 远程模式
vector_store = ChromaVectorStore(host="localhost", port=8000)

learning_store = LearningStore(vector_store=vector_store)
await learning_store.initialize()
```
"""

from __future__ import annotations

import logging
from typing import Any

from datapillar_oneagentic.storage.learning_stores.base import (
    VectorRecord,
    VectorSearchResult,
    VectorStore,
)

logger = logging.getLogger(__name__)

COLLECTION_NAME = "vectors"


class ChromaVectorStore(VectorStore):
    """
    Chroma 向量存储

    支持本地（PersistentClient）和远程（AsyncHttpClient）两种模式。
    """

    def __init__(
        self,
        *,
        path: str | None = None,
        host: str | None = None,
        port: int = 8000,
    ):
        """
        初始化 Chroma 存储

        Args:
            path: 本地数据库路径（本地模式）
            host: 远程服务器地址（远程模式）
            port: 远程服务器端口，默认 8000

        注意：path 和 host 二选一，优先使用 host（远程模式）
        """
        if not path and not host:
            path = "./data/chroma"

        self._path = path
        self._host = host
        self._port = port
        self._is_remote = host is not None
        self._client = None
        self._collection = None

    async def initialize(self) -> None:
        """初始化数据库和集合"""
        try:
            import chromadb
        except ImportError:
            raise ImportError("需要安装 Chroma 依赖：pip install chromadb")

        if self._is_remote:
            logger.info(f"初始化 ChromaVectorStore (远程): {self._host}:{self._port}")
            self._client = await chromadb.AsyncHttpClient(
                host=self._host,
                port=self._port,
            )
            self._collection = await self._client.get_or_create_collection(
                name=COLLECTION_NAME,
                metadata={"hnsw:space": "cosine"},
            )
        else:
            logger.info(f"初始化 ChromaVectorStore (本地): {self._path}")
            self._client = chromadb.PersistentClient(path=self._path)
            self._collection = self._client.get_or_create_collection(
                name=COLLECTION_NAME,
                metadata={"hnsw:space": "cosine"},
            )

        logger.info("ChromaVectorStore 初始化完成")

    async def close(self) -> None:
        """关闭连接"""
        self._client = None
        self._collection = None
        logger.info("ChromaVectorStore 已关闭")

    # ==================== 内部方法 ====================

    async def _add(self, **kwargs) -> None:
        """统一的添加方法"""
        if self._is_remote:
            await self._collection.add(**kwargs)
        else:
            self._collection.add(**kwargs)

    async def _update(self, **kwargs) -> None:
        """统一的更新方法"""
        if self._is_remote:
            await self._collection.update(**kwargs)
        else:
            self._collection.update(**kwargs)

    async def _delete(self, **kwargs) -> None:
        """统一的删除方法"""
        if self._is_remote:
            await self._collection.delete(**kwargs)
        else:
            self._collection.delete(**kwargs)

    async def _get(self, **kwargs) -> dict:
        """统一的获取方法"""
        if self._is_remote:
            return await self._collection.get(**kwargs)
        else:
            return self._collection.get(**kwargs)

    async def _query(self, **kwargs) -> dict:
        """统一的查询方法"""
        if self._is_remote:
            return await self._collection.query(**kwargs)
        else:
            return self._collection.query(**kwargs)

    async def _count(self) -> int:
        """统一的计数方法"""
        if self._is_remote:
            return await self._collection.count()
        else:
            return self._collection.count()

    def _build_where_clause(self, filter: dict[str, Any] | None) -> dict[str, Any] | None:
        """构建 Chroma where 过滤条件"""
        if not filter:
            return None

        conditions = []

        for key, value in filter.items():
            if isinstance(value, dict):
                for op, val in value.items():
                    conditions.append({key: {op: val}})
            else:
                conditions.append({key: {"$eq": value}})

        if len(conditions) == 1:
            return conditions[0]

        return {"$and": conditions}

    # ==================== 写操作 ====================

    async def add(self, record: VectorRecord) -> str:
        """添加记录"""
        await self._add(
            ids=[record.id],
            embeddings=[record.vector] if record.vector else None,
            documents=[record.text] if record.text else None,
            metadatas=[record.metadata] if record.metadata else None,
        )

        logger.debug(f"添加记录: {record.id}")
        return record.id

    async def add_batch(self, records: list[VectorRecord]) -> list[str]:
        """批量添加记录"""
        if not records:
            return []

        ids = [r.id for r in records]
        embeddings = [r.vector for r in records if r.vector]
        documents = [r.text for r in records]
        metadatas = [r.metadata for r in records]

        await self._add(
            ids=ids,
            embeddings=embeddings if embeddings else None,
            documents=documents,
            metadatas=metadatas,
        )

        logger.info(f"批量添加 {len(records)} 条记录")
        return ids

    async def update(self, record: VectorRecord) -> bool:
        """更新记录"""
        try:
            await self._update(
                ids=[record.id],
                embeddings=[record.vector] if record.vector else None,
                documents=[record.text] if record.text else None,
                metadatas=[record.metadata] if record.metadata else None,
            )
            logger.debug(f"更新记录: {record.id}")
            return True
        except Exception as e:
            logger.error(f"更新记录失败: {e}")
            return False

    async def delete(self, record_id: str) -> bool:
        """删除记录"""
        try:
            await self._delete(ids=[record_id])
            logger.debug(f"删除记录: {record_id}")
            return True
        except Exception as e:
            logger.error(f"删除记录失败: {e}")
            return False

    # ==================== 读操作 ====================

    async def get(self, record_id: str) -> VectorRecord | None:
        """获取记录"""
        result = await self._get(
            ids=[record_id],
            include=["embeddings", "documents", "metadatas"],
        )

        if not result["ids"]:
            return None

        return VectorRecord(
            id=result["ids"][0],
            vector=result["embeddings"][0] if result["embeddings"] else [],
            text=result["documents"][0] if result["documents"] else "",
            metadata=result["metadatas"][0] if result["metadatas"] else {},
        )

    async def search_by_vector(
        self,
        vector: list[float],
        k: int = 5,
        filter: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """向量相似度搜索"""
        where = self._build_where_clause(filter)

        result = await self._query(
            query_embeddings=[vector],
            n_results=k,
            where=where,
            include=["embeddings", "documents", "metadatas", "distances"],
        )

        results = []
        if result["ids"] and result["ids"][0]:
            for i, record_id in enumerate(result["ids"][0]):
                distance = result["distances"][0][i] if result["distances"] else 0
                score = 1.0 - distance  # cosine distance -> similarity

                results.append(VectorSearchResult(
                    id=record_id,
                    score=max(0, score),
                    distance=distance,
                    metadata=result["metadatas"][0][i] if result["metadatas"] else {},
                    text=result["documents"][0][i] if result["documents"] else "",
                ))

        return results

    async def search_by_text(
        self,
        query: str,
        k: int = 5,
        filter: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """全文搜索"""
        where = self._build_where_clause(filter)

        result = await self._query(
            query_texts=[query],
            n_results=k,
            where=where,
            include=["embeddings", "documents", "metadatas", "distances"],
        )

        results = []
        if result["ids"] and result["ids"][0]:
            for i, record_id in enumerate(result["ids"][0]):
                distance = result["distances"][0][i] if result["distances"] else 0
                score = 1.0 - distance

                results.append(VectorSearchResult(
                    id=record_id,
                    score=max(0, score),
                    distance=distance,
                    metadata=result["metadatas"][0][i] if result["metadatas"] else {},
                    text=result["documents"][0][i] if result["documents"] else "",
                ))

        return results

    # ==================== 统计操作 ====================

    async def count(self, filter: dict[str, Any] | None = None) -> int:
        """统计记录数量"""
        if filter:
            where = self._build_where_clause(filter)
            result = await self._get(where=where)
            return len(result["ids"])

        return await self._count()

    async def distinct(self, field: str) -> list[Any]:
        """获取字段的去重值列表"""
        result = await self._get(include=["metadatas"])

        values = set()
        for metadata in result["metadatas"]:
            if metadata and field in metadata:
                value = metadata[field]
                if isinstance(value, list):
                    values.update(value)
                else:
                    values.add(value)

        return list(values)
