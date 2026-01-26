"""KnowledgeStore abstract interface."""

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
    """Abstract interface for knowledge storage."""

    @property
    @abstractmethod
    def namespace(self) -> str:
        """Namespace (isolation boundary)."""

    @abstractmethod
    async def initialize(self) -> None:
        """Initialize the store."""

    @abstractmethod
    async def close(self) -> None:
        """Close the store."""

    @abstractmethod
    async def upsert_sources(self, sources: list[KnowledgeSource]) -> None:
        """Upsert knowledge sources."""

    @abstractmethod
    async def upsert_docs(self, docs: list[KnowledgeDocument]) -> None:
        """Upsert document metadata."""

    @abstractmethod
    async def upsert_chunks(self, chunks: list[KnowledgeChunk]) -> None:
        """Upsert knowledge chunks."""

    @abstractmethod
    async def search_chunks(
        self,
        *,
        query_vector: list[float],
        k: int,
        filters: dict[str, Any] | None = None,
    ) -> list[KnowledgeSearchHit]:
        """Search chunks."""

    @abstractmethod
    async def get_doc(self, doc_id: str) -> KnowledgeDocument | None:
        """Get document metadata."""

    @abstractmethod
    async def get_chunks(self, chunk_ids: list[str]) -> list[KnowledgeChunk]:
        """Get chunks by ID."""

    @abstractmethod
    async def delete_doc(self, doc_id: str) -> int:
        """Delete document metadata."""

    @abstractmethod
    async def delete_doc_chunks(self, doc_id: str) -> int:
        """Delete chunks by document ID."""
