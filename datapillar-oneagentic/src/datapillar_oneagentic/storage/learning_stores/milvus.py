"""
MilvusVectorStore - Milvus 向量存储

支持本地和远程两种模式：
- 本地模式：使用 Milvus Lite，嵌入式存储
- 远程模式：连接 Milvus Server

依赖：pip install pymilvus>=2.5.3

使用示例：
```python
from datapillar_oneagentic.storage.learning_stores import (
    LearningStore,
    MilvusVectorStore,
)

# 本地模式 (Milvus Lite)
vector_store = MilvusVectorStore(uri="./data/milvus.db")

# 远程模式
vector_store = MilvusVectorStore(uri="http://localhost:19530", token="root:Milvus")

learning_store = LearningStore(vector_store=vector_store)
await learning_store.initialize()
```
"""

from __future__ import annotations

import json
import logging
from typing import Any

from datapillar_oneagentic.storage.learning_stores.base import (
    VectorRecord,
    VectorSearchResult,
    VectorStore,
)

logger = logging.getLogger(__name__)

COLLECTION_NAME = "vectors"


def _get_embedding_dimension() -> int:
    """获取 Embedding 维度"""
    from datapillar_oneagentic.config import datapillar
    return datapillar.embedding.dimension


class MilvusVectorStore(VectorStore):
    """
    Milvus 向量存储

    支持本地（Milvus Lite）和远程（Milvus Server）两种模式。
    """

    def __init__(
        self,
        *,
        uri: str = "./data/milvus.db",
        token: str | None = None,
        dim: int | None = None,
    ):
        """
        初始化 Milvus 存储

        Args:
            uri: 连接地址
                - 本地模式：文件路径，如 "./data/milvus.db"
                - 远程模式：服务器地址，如 "http://localhost:19530"
            token: 认证令牌（远程模式需要，如 "root:Milvus"）
            dim: 向量维度（None 时读全局配置 datapillar.embedding.dimension）
        """
        self._uri = uri
        self._token = token
        self._dim = dim
        self._is_remote = uri.startswith("http")
        self._client = None

    async def initialize(self) -> None:
        """初始化数据库和集合"""
        try:
            from pymilvus import AsyncMilvusClient, DataType
        except ImportError:
            raise ImportError("需要安装 Milvus 依赖：pip install pymilvus>=2.5.3")

        if self._is_remote:
            logger.info(f"初始化 MilvusVectorStore (远程): {self._uri}")
            self._client = AsyncMilvusClient(uri=self._uri, token=self._token)
        else:
            logger.info(f"初始化 MilvusVectorStore (本地): {self._uri}")
            self._client = AsyncMilvusClient(uri=self._uri)

        # 检查集合是否存在
        has_collection = await self._client.has_collection(COLLECTION_NAME)

        if not has_collection:
            # 创建集合
            from pymilvus import MilvusClient

            # 获取向量维度
            dim = self._dim if self._dim is not None else _get_embedding_dimension()

            schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=True)
            schema.add_field("id", DataType.VARCHAR, is_primary=True, max_length=128)
            schema.add_field("text", DataType.VARCHAR, max_length=65535)
            schema.add_field("vector", DataType.FLOAT_VECTOR, dim=dim)
            schema.add_field("metadata", DataType.VARCHAR, max_length=65535)

            index_params = MilvusClient.prepare_index_params()
            index_params.add_index(
                field_name="vector",
                index_type="FLAT",
                metric_type="COSINE",
            )

            await self._client.create_collection(
                collection_name=COLLECTION_NAME,
                schema=schema,
                index_params=index_params,
            )
            logger.info(f"创建集合: {COLLECTION_NAME}")
        else:
            logger.info(f"打开已存在的集合: {COLLECTION_NAME}")

        logger.info("MilvusVectorStore 初始化完成")

    async def close(self) -> None:
        """关闭连接"""
        if self._client:
            await self._client.close()
        self._client = None
        logger.info("MilvusVectorStore 已关闭")

    # ==================== 写操作 ====================

    async def add(self, record: VectorRecord) -> str:
        """添加记录"""
        data = {
            "id": record.id,
            "text": record.text,
            "vector": record.vector,
            "metadata": json.dumps(record.metadata, ensure_ascii=False),
        }

        await self._client.insert(
            collection_name=COLLECTION_NAME,
            data=[data],
        )

        logger.debug(f"添加记录: {record.id}")
        return record.id

    async def add_batch(self, records: list[VectorRecord]) -> list[str]:
        """批量添加记录"""
        if not records:
            return []

        data = [
            {
                "id": r.id,
                "text": r.text,
                "vector": r.vector,
                "metadata": json.dumps(r.metadata, ensure_ascii=False),
            }
            for r in records
        ]

        await self._client.insert(
            collection_name=COLLECTION_NAME,
            data=data,
        )

        logger.info(f"批量添加 {len(records)} 条记录")
        return [r.id for r in records]

    async def update(self, record: VectorRecord) -> bool:
        """更新记录"""
        try:
            await self.delete(record.id)
            await self.add(record)
            logger.debug(f"更新记录: {record.id}")
            return True
        except Exception as e:
            logger.error(f"更新记录失败: {e}")
            return False

    async def delete(self, record_id: str) -> bool:
        """删除记录"""
        try:
            await self._client.delete(
                collection_name=COLLECTION_NAME,
                filter=f'id == "{record_id}"',
            )
            logger.debug(f"删除记录: {record_id}")
            return True
        except Exception as e:
            logger.error(f"删除记录失败: {e}")
            return False

    # ==================== 读操作 ====================

    async def get(self, record_id: str) -> VectorRecord | None:
        """获取记录"""
        result = await self._client.get(
            collection_name=COLLECTION_NAME,
            ids=[record_id],
            output_fields=["id", "text", "vector", "metadata"],
        )

        if not result:
            return None

        row = result[0]
        return VectorRecord(
            id=row["id"],
            text=row.get("text", ""),
            vector=row.get("vector", []),
            metadata=json.loads(row.get("metadata", "{}")),
        )

    def _build_filter_expr(self, filter: dict[str, Any] | None) -> str | None:
        """构建 Milvus 过滤表达式"""
        if not filter:
            return None

        conditions = []

        for key, value in filter.items():
            if isinstance(value, dict):
                for op, val in value.items():
                    if op == "$eq":
                        if isinstance(val, str):
                            conditions.append(f'json_contains(metadata, \'"{key}": "{val}"\')')
                        else:
                            conditions.append(f'json_contains(metadata, \'"{key}": {val}\')')
                    elif op == "$gte":
                        conditions.append(f'JSON_EXTRACT(metadata, "$.{key}") >= {val}')
                    elif op == "$lte":
                        conditions.append(f'JSON_EXTRACT(metadata, "$.{key}") <= {val}')
                    elif op == "$gt":
                        conditions.append(f'JSON_EXTRACT(metadata, "$.{key}") > {val}')
                    elif op == "$lt":
                        conditions.append(f'JSON_EXTRACT(metadata, "$.{key}") < {val}')
                    elif op == "$contains":
                        conditions.append(f'metadata like "%{val}%"')
            else:
                if isinstance(value, str):
                    conditions.append(f'json_contains(metadata, \'"{key}": "{value}"\')')
                else:
                    conditions.append(f'json_contains(metadata, \'"{key}": {value}\')')

        if not conditions:
            return None

        return " and ".join(conditions)

    async def search_by_vector(
        self,
        vector: list[float],
        k: int = 5,
        filter: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """向量相似度搜索"""
        filter_expr = self._build_filter_expr(filter)

        result = await self._client.search(
            collection_name=COLLECTION_NAME,
            data=[vector],
            limit=k,
            filter=filter_expr,
            output_fields=["id", "text", "metadata"],
        )

        results = []
        if result and result[0]:
            for hit in result[0]:
                distance = hit.get("distance", 0)
                score = 1.0 - distance  # cosine distance -> similarity
                metadata = json.loads(hit["entity"].get("metadata", "{}"))

                results.append(VectorSearchResult(
                    id=hit["entity"]["id"],
                    score=max(0, score),
                    distance=distance,
                    metadata=metadata,
                    text=hit["entity"].get("text", ""),
                ))

        return results

    async def search_by_text(
        self,
        query: str,
        k: int = 5,
        filter: dict[str, Any] | None = None,
    ) -> list[VectorSearchResult]:
        """
        全文搜索

        注意：Milvus 原生不支持全文搜索，这里通过 text 字段模糊匹配实现。
        如需真正的全文搜索，建议使用 LanceDB 或 Chroma。
        """
        filter_expr = self._build_filter_expr(filter)

        # 添加文本模糊匹配条件
        text_filter = f'text like "%{query}%"'
        if filter_expr:
            filter_expr = f"({filter_expr}) and ({text_filter})"
        else:
            filter_expr = text_filter

        result = await self._client.query(
            collection_name=COLLECTION_NAME,
            filter=filter_expr,
            limit=k,
            output_fields=["id", "text", "vector", "metadata"],
        )

        results = []
        for row in result:
            metadata = json.loads(row.get("metadata", "{}"))

            results.append(VectorSearchResult(
                id=row["id"],
                score=1.0,  # 模糊匹配无相似度分数
                distance=None,
                metadata=metadata,
                text=row.get("text", ""),
            ))

        return results

    # ==================== 统计操作 ====================

    async def count(self, filter: dict[str, Any] | None = None) -> int:
        """统计记录数量"""
        filter_expr = self._build_filter_expr(filter)

        if filter_expr:
            result = await self._client.query(
                collection_name=COLLECTION_NAME,
                filter=filter_expr,
                output_fields=["id"],
            )
            return len(result)

        # 无过滤条件时使用 count
        stats = await self._client.get_collection_stats(COLLECTION_NAME)
        return stats.get("row_count", 0)

    async def distinct(self, field: str) -> list[Any]:
        """获取字段的去重值列表"""
        result = await self._client.query(
            collection_name=COLLECTION_NAME,
            filter="id != ''",
            output_fields=["metadata"],
        )

        values = set()
        for row in result:
            metadata = json.loads(row.get("metadata", "{}"))
            if field in metadata:
                value = metadata[field]
                if isinstance(value, list):
                    values.update(value)
                else:
                    values.add(value)

        return list(values)
