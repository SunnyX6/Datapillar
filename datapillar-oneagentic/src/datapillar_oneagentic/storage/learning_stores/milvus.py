"""
MilvusExperienceStore - Milvus 实现

支持本地和远程两种模式：
- 本地模式：使用 Milvus Lite，嵌入式存储
- 远程模式：连接 Milvus Server

依赖：pip install pymilvus>=2.5.3
"""

from __future__ import annotations

import json
import logging
from typing import Any, TYPE_CHECKING

from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore

if TYPE_CHECKING:
    from datapillar_oneagentic.experience.learner import ExperienceRecord

logger = logging.getLogger(__name__)

_BASE_COLLECTION_NAME = "experiences"


def _get_embedding_dimension() -> int:
    """获取 Embedding 维度"""
    from datapillar_oneagentic.config import datapillar
    return datapillar.embedding.dimension


class MilvusExperienceStore(ExperienceStore):
    """
    Milvus 经验存储实现

    支持本地（Milvus Lite）和远程（Milvus Server）两种模式。
    """

    def __init__(
        self,
        *,
        uri: str = "./data/milvus.db",
        token: str | None = None,
        dim: int | None = None,
        namespace: str = "default",
    ):
        """
        初始化 Milvus 存储

        Args:
            uri: 连接地址
                - 本地模式：文件路径，如 "./data/milvus.db"
                - 远程模式：服务器地址，如 "http://localhost:19530"
            token: 认证令牌（远程模式需要，如 "root:Milvus"）
            dim: 向量维度（None 时读全局配置 datapillar.embedding.dimension）
            namespace: 命名空间（用于隔离经验数据）
        """
        import os

        self._namespace = namespace
        self._is_remote = uri.startswith("http")

        if not self._is_remote:
            base_path = os.path.dirname(uri) or "."
            filename = os.path.basename(uri)
            self._uri = os.path.join(base_path, namespace, filename)
        else:
            self._uri = uri

        self._token = token
        self._dim = dim
        self._client = None
        self._collection_name = f"{namespace}_{_BASE_COLLECTION_NAME}"

    async def initialize(self) -> None:
        """初始化数据库和集合"""
        try:
            from pymilvus import AsyncMilvusClient, DataType
        except ImportError:
            raise ImportError("需要安装 Milvus 依赖：pip install pymilvus>=2.5.3")

        if self._is_remote:
            logger.info(f"初始化 MilvusExperienceStore (远程): {self._uri}, namespace={self._namespace}")
            self._client = AsyncMilvusClient(uri=self._uri, token=self._token)
        else:
            logger.info(f"初始化 MilvusExperienceStore (本地): {self._uri}")
            import os
            os.makedirs(os.path.dirname(self._uri), exist_ok=True)
            self._client = AsyncMilvusClient(uri=self._uri)

        has_collection = await self._client.has_collection(self._collection_name)

        if not has_collection:
            from pymilvus import MilvusClient

            dim = self._dim if self._dim is not None else _get_embedding_dimension()

            schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=True)
            schema.add_field("id", DataType.VARCHAR, is_primary=True, max_length=128)
            schema.add_field("namespace", DataType.VARCHAR, max_length=128)
            schema.add_field("session_id", DataType.VARCHAR, max_length=128)
            schema.add_field("goal", DataType.VARCHAR, max_length=65535)
            schema.add_field("outcome", DataType.VARCHAR, max_length=32)
            schema.add_field("result_summary", DataType.VARCHAR, max_length=65535)
            schema.add_field("tools_used", DataType.VARCHAR, max_length=65535)
            schema.add_field("agents_involved", DataType.VARCHAR, max_length=65535)
            schema.add_field("duration_ms", DataType.INT64)
            schema.add_field("feedback", DataType.VARCHAR, max_length=65535)
            schema.add_field("created_at", DataType.INT64)
            schema.add_field("vector", DataType.FLOAT_VECTOR, dim=dim)

            index_params = MilvusClient.prepare_index_params()
            index_params.add_index(
                field_name="vector",
                index_type="FLAT",
                metric_type="COSINE",
            )

            await self._client.create_collection(
                collection_name=self._collection_name,
                schema=schema,
                index_params=index_params,
            )
            logger.info(f"创建集合: {self._collection_name}")
        else:
            logger.info(f"打开已存在的集合: {self._collection_name}")

        logger.info("MilvusExperienceStore 初始化完成")

    async def close(self) -> None:
        """关闭连接"""
        if self._client:
            await self._client.close()
        self._client = None
        logger.info("MilvusExperienceStore 已关闭")

    def _record_to_milvus(self, record: "ExperienceRecord") -> dict[str, Any]:
        """将 ExperienceRecord 转换为 Milvus 格式"""
        return {
            "id": record.id,
            "namespace": record.namespace,
            "session_id": record.session_id,
            "goal": record.goal,
            "outcome": record.outcome,
            "result_summary": record.result_summary,
            "tools_used": json.dumps(record.tools_used, ensure_ascii=False),
            "agents_involved": json.dumps(record.agents_involved, ensure_ascii=False),
            "duration_ms": record.duration_ms,
            "feedback": json.dumps(record.feedback, ensure_ascii=False),
            "created_at": record.created_at,
            "vector": record.vector,
        }

    def _milvus_to_record(self, row: dict[str, Any]) -> "ExperienceRecord":
        """将 Milvus 格式转换为 ExperienceRecord"""
        from datapillar_oneagentic.experience.learner import ExperienceRecord

        return ExperienceRecord(
            id=row["id"],
            namespace=row.get("namespace", ""),
            session_id=row.get("session_id", ""),
            goal=row.get("goal", ""),
            outcome=row.get("outcome", "pending"),
            result_summary=row.get("result_summary", ""),
            tools_used=json.loads(row.get("tools_used", "[]")),
            agents_involved=json.loads(row.get("agents_involved", "[]")),
            duration_ms=row.get("duration_ms", 0),
            feedback=json.loads(row.get("feedback", "{}")),
            created_at=row.get("created_at", 0),
            vector=row.get("vector", []),
        )

    # ==================== 写操作 ====================

    async def add(self, record: "ExperienceRecord") -> str:
        """添加记录"""
        data = self._record_to_milvus(record)

        await self._client.insert(
            collection_name=self._collection_name,
            data=[data],
        )

        return record.id

    async def delete(self, record_id: str) -> bool:
        """删除记录"""
        try:
            await self._client.delete(
                collection_name=self._collection_name,
                filter=f'id == "{record_id}"',
            )
            return True
        except Exception as e:
            logger.error(f"删除记录失败: {e}")
            return False

    # ==================== 读操作 ====================

    async def get(self, record_id: str) -> "ExperienceRecord | None":
        """获取记录"""
        result = await self._client.get(
            collection_name=self._collection_name,
            ids=[record_id],
            output_fields=["*"],
        )

        if not result:
            return None

        return self._milvus_to_record(result[0])

    async def search(
        self,
        query_vector: list[float],
        k: int = 5,
        outcome: str | None = None,
    ) -> list["ExperienceRecord"]:
        """
        向量相似度搜索

        Args:
            query_vector: 查询向量
            k: 返回数量
            outcome: 过滤条件（success / failure / None=全部）

        Returns:
            ExperienceRecord 列表（按相似度排序）
        """
        filter_expr = None
        if outcome:
            filter_expr = f'outcome == "{outcome}"'

        result = await self._client.search(
            collection_name=self._collection_name,
            data=[query_vector],
            limit=k,
            filter=filter_expr,
            output_fields=["*"],
        )

        records = []
        if result and result[0]:
            for hit in result[0]:
                entity = hit.get("entity", {})
                entity["id"] = hit.get("id", entity.get("id"))
                records.append(self._milvus_to_record(entity))

        return records

    # ==================== 统计操作 ====================

    async def count(self) -> int:
        """统计记录数量"""
        stats = await self._client.get_collection_stats(self._collection_name)
        return stats.get("row_count", 0)
