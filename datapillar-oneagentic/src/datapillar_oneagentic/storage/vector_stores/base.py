"""VectorStore abstract interface."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from enum import Enum
from typing import Any


class VectorFieldType(str, Enum):
    STRING = "string"
    INT = "int"
    FLOAT = "float"
    JSON = "json"
    VECTOR = "vector"
    SPARSE_VECTOR = "sparse_vector"


@dataclass(frozen=True)
class VectorField:
    name: str
    field_type: VectorFieldType
    dimension: int | None = None


@dataclass(frozen=True)
class VectorCollectionSchema:
    name: str
    primary_key: str
    fields: list[VectorField]


@dataclass(frozen=True)
class VectorStoreCapabilities:
    supports_dense: bool = True
    supports_sparse: bool = True
    supports_filter: bool = True


@dataclass(frozen=True)
class VectorSearchResult:
    record: dict[str, Any]
    score: float
    score_kind: str


class VectorStore(ABC):
    """Unified vector database interface."""

    def __init__(self, *, namespace: str) -> None:
        self._namespace = namespace
        self._schemas: dict[str, VectorCollectionSchema] = {}

    @property
    def namespace(self) -> str:
        return self._namespace

    @property
    @abstractmethod
    def capabilities(self) -> VectorStoreCapabilities:
        """Capability declaration."""

    def register_schema(self, schema: VectorCollectionSchema) -> None:
        """Register collection schema."""
        self._schemas[schema.name] = schema

    def get_schema(self, name: str) -> VectorCollectionSchema:
        schema = self._schemas.get(name)
        if not schema:
            raise KeyError(f"Collection schema not registered: {name}")
        return schema

    def _namespaced(self, name: str) -> str:
        return f"{self._namespace}_{name}"

    @abstractmethod
    async def initialize(self) -> None:
        """Initialize connection."""

    @abstractmethod
    async def close(self) -> None:
        """Close connection."""

    @abstractmethod
    async def ensure_collection(self, schema: VectorCollectionSchema) -> None:
        """Ensure collection exists."""

    @abstractmethod
    async def add(self, collection: str, records: list[dict[str, Any]]) -> None:
        """Insert records."""

    @abstractmethod
    async def get(self, collection: str, ids: list[str]) -> list[dict[str, Any]]:
        """Get records by ID."""

    @abstractmethod
    async def delete(self, collection: str, ids: list[str]) -> int:
        """Delete records."""

    @abstractmethod
    async def search(
        self,
        collection: str,
        query_vector: list[float],
        k: int = 5,
        filters: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """Vector search."""

    @abstractmethod
    async def query(
        self,
        collection: str,
        filters: dict[str, Any] | None = None,
        limit: int | None = None,
    ) -> list[dict[str, Any]]:
        """Query by filters."""

    @abstractmethod
    async def count(self, collection: str) -> int:
        """Count records in a collection."""
