"""
KnowledgeStore 抽象接口
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any

from datapillar_oneagentic.knowledge.models import (
    KnowledgeChunk,
    KnowledgeDocument,
    KnowledgeSearchHit,
    KnowledgeSource,
)


class KnowledgeStore(ABC):
    """知识存储抽象接口"""

    @abstractmethod
    async def initialize(self) -> None:
        """初始化存储"""

    @abstractmethod
    async def close(self) -> None:
        """关闭存储"""

    @abstractmethod
    async def upsert_sources(self, sources: list[KnowledgeSource]) -> None:
        """写入知识源"""

    @abstractmethod
    async def upsert_docs(self, docs: list[KnowledgeDocument]) -> None:
        """写入文档元数据"""

    @abstractmethod
    async def upsert_chunks(self, chunks: list[KnowledgeChunk]) -> None:
        """写入知识分片"""

    @abstractmethod
    async def search_chunks(
        self,
        *,
        query_vector: list[float],
        k: int,
        filters: dict[str, Any] | None = None,
    ) -> list[KnowledgeSearchHit]:
        """检索分片"""

    @abstractmethod
    async def get_doc(self, doc_id: str) -> KnowledgeDocument | None:
        """获取文档元数据"""

    @abstractmethod
    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        """按 ID 获取分片"""
