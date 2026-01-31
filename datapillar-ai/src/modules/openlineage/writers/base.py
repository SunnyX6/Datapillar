# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
写入器基类
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from neo4j import AsyncSession


@dataclass
class WriterStats:
    """写入器统计"""

    total_writes: int = 0
    successful_writes: int = 0
    failed_writes: int = 0
    last_write_time: datetime | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "total_writes": self.total_writes,
            "successful_writes": self.successful_writes,
            "failed_writes": self.failed_writes,
            "success_rate": (
                self.successful_writes / self.total_writes if self.total_writes > 0 else 0
            ),
            "last_write_time": self.last_write_time.isoformat() if self.last_write_time else None,
        }


class BaseWriter(ABC):
    """
    写入器基类

    定义写入 Neo4j 的通用接口
    """

    def __init__(self) -> None:
        self._stats = WriterStats()

    @property
    @abstractmethod
    def name(self) -> str:
        """写入器名称"""
        pass

    @abstractmethod
    async def write(self, session: AsyncSession, plans: Any) -> None:
        """
        写入计划（plans）

        Args:
            session: Neo4j 异步会话
            plans: 写入计划（由 event_processor 预先解析得到）
        """
        pass

    async def safe_write(self, session: AsyncSession, plans: Any) -> bool:
        """
        安全写入（捕获异常）

        Args:
            session: Neo4j 异步会话
            plans: 写入计划

        Returns:
            是否写入成功
        """
        self._stats.total_writes += 1
        self._stats.last_write_time = datetime.now(UTC)

        try:
            await self.write(session, plans)
            self._stats.successful_writes += 1
            return True
        except Exception:
            self._stats.failed_writes += 1
            return False

    def get_stats(self) -> WriterStats:
        """获取统计信息"""
        return self._stats

    def reset_stats(self) -> None:
        """重置统计"""
        self._stats = WriterStats()
