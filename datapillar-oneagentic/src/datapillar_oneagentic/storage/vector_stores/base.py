"""
VectorStore 抽象接口
"""

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
    """向量数据库统一接口"""

    def __init__(self, *, namespace: str) -> None:
        self._namespace = namespace
        self._schemas: dict[str, VectorCollectionSchema] = {}

    @property
    def namespace(self) -> str:
        return self._namespace

    @property
    @abstractmethod
    def capabilities(self) -> VectorStoreCapabilities:
        """能力声明"""

    def register_schema(self, schema: VectorCollectionSchema) -> None:
        """注册集合 schema"""
        self._schemas[schema.name] = schema

    def get_schema(self, name: str) -> VectorCollectionSchema:
        schema = self._schemas.get(name)
        if not schema:
            raise KeyError(f"未注册集合 schema: {name}")
        return schema

    def _namespaced(self, name: str) -> str:
        return f"{self._namespace}_{name}"

    @abstractmethod
    async def initialize(self) -> None:
        """初始化连接"""

    @abstractmethod
    async def close(self) -> None:
        """关闭连接"""

    @abstractmethod
    async def ensure_collection(self, schema: VectorCollectionSchema) -> None:
        """确保集合存在"""

    @abstractmethod
    async def add(self, collection: str, records: list[dict[str, Any]]) -> None:
        """写入记录"""

    @abstractmethod
    async def get(self, collection: str, ids: list[str]) -> list[dict[str, Any]]:
        """按 ID 获取记录"""

    @abstractmethod
    async def delete(self, collection: str, ids: list[str]) -> int:
        """删除记录"""

    @abstractmethod
    async def search(
        self,
        collection: str,
        query_vector: list[float],
        k: int = 5,
        filters: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """向量搜索"""

    @abstractmethod
    async def query(
        self,
        collection: str,
        filters: dict[str, Any] | None = None,
        limit: int | None = None,
    ) -> list[dict[str, Any]]:
        """按过滤条件查询"""

    @abstractmethod
    async def count(self, collection: str) -> int:
        """统计集合记录数"""
