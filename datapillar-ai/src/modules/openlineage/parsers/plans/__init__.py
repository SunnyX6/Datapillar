# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage 写入计划（plans）

目标：
- 将 RunEvent 解析为“可直接写入 Neo4j 的计划对象”
- event_processor 负责产出 plans；writers 只消费 plans 并写入
"""

from src.modules.openlineage.parsers.plans.builder import OpenLineagePlanBuilder
from src.modules.openlineage.parsers.plans.types import (
    LineageWritePlans,
    MetadataWritePlans,
    OpenLineageWritePlans,
)

__all__ = [
    "LineageWritePlans",
    "MetadataWritePlans",
    "OpenLineagePlanBuilder",
    "OpenLineageWritePlans",
]
