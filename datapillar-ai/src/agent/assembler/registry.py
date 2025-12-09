"""
组件注册器 - 管理所有组件组装器
"""

from typing import Dict
import logging

logger = logging.getLogger(__name__)
from .base import ComponentAssembler


class ComponentRegistry:
    """
    组件注册器

    职责：
    1. 注册所有组件组装器
    2. 根据组件类型获取对应的组装器
    3. 列出所有已注册的组件

    使用方式：
        registry = ComponentRegistry.get_instance()
        registry.register(MySQLAssembler())
        assembler = registry.get("mysql")
    """

    _instance = None
    _assemblers: Dict[str, ComponentAssembler] = {}

    def __new__(cls):
        """单例模式"""
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    @classmethod
    def get_instance(cls) -> "ComponentRegistry":
        """获取单例实例"""
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def register(self, assembler: ComponentAssembler) -> None:
        """
        注册组件组装器

        Args:
            assembler: 组件组装器实例

        Raises:
            ValueError: 如果组件类型已注册
        """
        component_type = assembler.component_type

        if component_type in self._assemblers:
            logger.warning(f"组件 {component_type} 已注册，将被覆盖")

        self._assemblers[component_type] = assembler
        logger.info(f"✅ 注册组件组装器: {component_type}")

    def get(self, component_type: str) -> ComponentAssembler:
        """
        获取组件组装器

        Args:
            component_type: 组件类型名称

        Returns:
            组件组装器实例

        Raises:
            ValueError: 如果组件类型未注册
        """
        if component_type not in self._assemblers:
            available = ", ".join(self._assemblers.keys())
            raise ValueError(f"未注册的组件类型: {component_type}。可用组件: {available}")

        return self._assemblers[component_type]

    def has(self, component_type: str) -> bool:
        """
        检查组件是否已注册

        Args:
            component_type: 组件类型名称

        Returns:
            True 如果已注册，False 否则
        """
        return component_type in self._assemblers

    def list_components(self) -> list:
        """
        列出所有已注册的组件

        Returns:
            组件类型名称列表
        """
        return list(self._assemblers.keys())

    def clear(self) -> None:
        """清空所有注册的组件（主要用于测试）"""
        self._assemblers.clear()
        logger.info("清空所有组件注册")
