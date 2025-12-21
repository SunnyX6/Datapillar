"""
解析器基类
"""

from abc import ABC, abstractmethod
from typing import Any, Generic, TypeVar

from src.modules.openlineage.schemas.events import RunEvent

T = TypeVar("T")


class BaseFacetParser(ABC, Generic[T]):
    """
    Facet 解析器基类

    负责从 OpenLineage 事件中提取特定 Facet 并转换为目标数据结构
    """

    @property
    @abstractmethod
    def facet_name(self) -> str:
        """Facet 名称"""
        pass

    @abstractmethod
    def can_parse(self, event: RunEvent) -> bool:
        """检查事件是否包含可解析的 Facet"""
        pass

    @abstractmethod
    def parse(self, event: RunEvent) -> list[T]:
        """
        解析事件

        Args:
            event: OpenLineage 事件

        Returns:
            解析结果列表
        """
        pass

    def safe_parse(self, event: RunEvent) -> list[T]:
        """
        安全解析（捕获异常）

        Args:
            event: OpenLineage 事件

        Returns:
            解析结果列表，解析失败返回空列表
        """
        if not self.can_parse(event):
            return []

        try:
            return self.parse(event)
        except Exception:
            return []
