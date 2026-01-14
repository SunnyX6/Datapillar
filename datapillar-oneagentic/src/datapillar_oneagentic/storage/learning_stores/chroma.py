"""
ChromaExperienceStore - Chroma 实现

支持本地和远程两种模式：
- 本地模式：使用 PersistentClient，嵌入式存储
- 远程模式：使用 AsyncHttpClient，连接 Chroma Server

依赖：pip install chromadb
"""

from __future__ import annotations

import json
import logging
from typing import TYPE_CHECKING, Any

from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore

if TYPE_CHECKING:
    from datapillar_oneagentic.experience.learner import ExperienceRecord

logger = logging.getLogger(__name__)

COLLECTION_NAME = "experiences"


class ChromaExperienceStore(ExperienceStore):
    """
    Chroma 经验存储实现

    支持本地（PersistentClient）和远程（AsyncHttpClient）两种模式。
    """

    def __init__(
        self,
        *,
        path: str | None = None,
        host: str | None = None,
        port: int = 8000,
        namespace: str = "default",
    ):
        """
        初始化 Chroma 存储

        Args:
            path: 本地数据库路径（本地模式）
            host: 远程服务器地址（远程模式）
            port: 远程服务器端口，默认 8000
            namespace: 命名空间（用于隔离经验数据）

        注意：path 和 host 二选一，优先使用 host（远程模式）
        """
        import os

        if not path and not host:
            path = "./data/chroma"

        self._namespace = namespace
        if path:
            self._path = os.path.join(path, namespace)
        else:
            self._path = path
        self._host = host
        self._port = port
        self._is_remote = host is not None
        self._client = None
        self._collection = None
        self._collection_name = f"{namespace}_{COLLECTION_NAME}" if self._is_remote else COLLECTION_NAME

    async def initialize(self) -> None:
        """初始化数据库和集合"""
        try:
            import chromadb
        except ImportError as err:
            raise ImportError("需要安装 Chroma 依赖：pip install chromadb") from err

        if self._is_remote:
            logger.info(f"初始化 ChromaExperienceStore (远程): {self._host}:{self._port}, namespace={self._namespace}")
            self._client = await chromadb.AsyncHttpClient(
                host=self._host,
                port=self._port,
            )
            self._collection = await self._client.get_or_create_collection(
                name=self._collection_name,
                metadata={"hnsw:space": "cosine"},
            )
        else:
            logger.info(f"初始化 ChromaExperienceStore (本地): {self._path}")
            self._client = chromadb.PersistentClient(path=self._path)
            self._collection = self._client.get_or_create_collection(
                name=self._collection_name,
                metadata={"hnsw:space": "cosine"},
            )

        logger.info("ChromaExperienceStore 初始化完成")

    async def close(self) -> None:
        """关闭连接"""
        self._client = None
        self._collection = None
        logger.info("ChromaExperienceStore 已关闭")

    # ==================== 内部方法 ====================

    async def _add(self, **kwargs) -> None:
        """统一的添加方法"""
        if self._is_remote:
            await self._collection.add(**kwargs)
        else:
            self._collection.add(**kwargs)

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

    def _record_to_chroma(self, record: ExperienceRecord) -> tuple[str, list[float], str, dict]:
        """将 ExperienceRecord 转换为 Chroma 格式"""
        metadata = {
            "namespace": record.namespace,
            "session_id": record.session_id,
            "outcome": record.outcome,
            "tools_used": json.dumps(record.tools_used, ensure_ascii=False),
            "agents_involved": json.dumps(record.agents_involved, ensure_ascii=False),
            "duration_ms": record.duration_ms,
            "feedback": json.dumps(record.feedback, ensure_ascii=False),
            "created_at": record.created_at,
        }
        document = f"目标: {record.goal}\n结果: {record.result_summary}"
        return record.id, record.vector, document, metadata

    def _chroma_to_record(
        self,
        record_id: str,
        embedding: list[float] | None,
        document: str | None,
        metadata: dict[str, Any] | None,
    ) -> ExperienceRecord:
        """将 Chroma 格式转换为 ExperienceRecord"""
        from datapillar_oneagentic.experience.learner import ExperienceRecord

        metadata = metadata or {}

        goal = ""
        result_summary = ""
        if document:
            lines = document.split("\n")
            for line in lines:
                if line.startswith("目标: "):
                    goal = line[4:]
                elif line.startswith("结果: "):
                    result_summary = line[4:]

        return ExperienceRecord(
            id=record_id,
            namespace=metadata.get("namespace", ""),
            session_id=metadata.get("session_id", ""),
            goal=goal,
            outcome=metadata.get("outcome", "pending"),
            result_summary=result_summary,
            tools_used=json.loads(metadata.get("tools_used", "[]")),
            agents_involved=json.loads(metadata.get("agents_involved", "[]")),
            duration_ms=metadata.get("duration_ms", 0),
            feedback=json.loads(metadata.get("feedback", "{}")),
            created_at=metadata.get("created_at", 0),
            vector=embedding or [],
        )

    # ==================== 写操作 ====================

    async def add(self, record: ExperienceRecord) -> str:
        """添加记录"""
        # 确保已初始化
        if self._collection is None:
            await self.initialize()

        record_id, embedding, document, metadata = self._record_to_chroma(record)

        await self._add(
            ids=[record_id],
            embeddings=[embedding] if embedding else None,
            documents=[document],
            metadatas=[metadata],
        )

        return record_id

    async def delete(self, record_id: str) -> bool:
        """删除记录"""
        # 确保已初始化
        if self._collection is None:
            await self.initialize()

        try:
            await self._delete(ids=[record_id])
            return True
        except Exception as e:
            logger.error(f"删除记录失败: {e}")
            return False

    # ==================== 读操作 ====================

    async def get(self, record_id: str) -> ExperienceRecord | None:
        """获取记录"""
        # 确保已初始化
        if self._collection is None:
            await self.initialize()

        result = await self._get(
            ids=[record_id],
            include=["embeddings", "documents", "metadatas"],
        )

        if not result["ids"]:
            return None

        return self._chroma_to_record(
            record_id=result["ids"][0],
            embedding=result["embeddings"][0] if result["embeddings"] else None,
            document=result["documents"][0] if result["documents"] else None,
            metadata=result["metadatas"][0] if result["metadatas"] else None,
        )

    async def search(
        self,
        query_vector: list[float],
        k: int = 5,
        outcome: str | None = None,
    ) -> list[ExperienceRecord]:
        """
        向量相似度搜索

        Args:
            query_vector: 查询向量
            k: 返回数量
            outcome: 过滤条件（success / failure / None=全部）

        Returns:
            ExperienceRecord 列表（按相似度排序）
        """
        # 确保已初始化
        if self._collection is None:
            await self.initialize()

        where = None
        if outcome:
            where = {"outcome": {"$eq": outcome}}

        result = await self._query(
            query_embeddings=[query_vector],
            n_results=k,
            where=where,
            include=["embeddings", "documents", "metadatas"],
        )

        records = []
        if result["ids"] and result["ids"][0]:
            for i, record_id in enumerate(result["ids"][0]):
                records.append(self._chroma_to_record(
                    record_id=record_id,
                    embedding=result["embeddings"][0][i] if result["embeddings"] else None,
                    document=result["documents"][0][i] if result["documents"] else None,
                    metadata=result["metadatas"][0][i] if result["metadatas"] else None,
                ))

        return records

    # ==================== 统计操作 ====================

    async def count(self) -> int:
        """统计记录数量"""
        # 确保已初始化
        if self._collection is None:
            await self.initialize()

        return await self._count()
