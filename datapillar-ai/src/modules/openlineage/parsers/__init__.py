"""
OpenLineage 解析层（parsers）

职责：
- 将 OpenLineage RunEvent 解析为“写入 plans”（无副作用）
- event_processor 只依赖 plans，writers 只消费 plans
"""

from src.modules.openlineage.parsers.plans import OpenLineagePlanBuilder

__all__ = [
    "OpenLineagePlanBuilder",
]
