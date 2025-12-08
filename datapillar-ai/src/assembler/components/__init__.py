"""
组件自动注册模块
导入时自动注册所有组件组装器
"""

from ..registry import ComponentRegistry
from .datax import DataXAssembler
from .hive import HiveAssembler
from .flink import FlinkAssembler
from .shell import ShellAssembler


def register_all_components():
    """注册所有组件组装器"""
    registry = ComponentRegistry.get_instance()

    # 注册所有可用组件
    registry.register(DataXAssembler())
    registry.register(HiveAssembler())
    registry.register(FlinkAssembler())
    registry.register(ShellAssembler())


# 模块导入时自动注册
register_all_components()
