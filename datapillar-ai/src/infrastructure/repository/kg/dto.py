"""
知识图谱 Repository DTO 定义

说明：
- DTO 只承载“跨层传输的最小数据结构”，避免在 queries/retrieval 文件中重复定义
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass
class WordRootDTO:
    """词根"""

    code: str
    name: str | None = None
    data_type: str | None = None
    description: str | None = None


@dataclass
class ModifierDTO:
    """修饰符"""

    code: str
    modifier_type: str | None = None
    description: str | None = None


@dataclass
class UnitDTO:
    """单位"""

    code: str
    name: str | None = None
    symbol: str | None = None
    description: str | None = None


@dataclass
class TableContextDTO:
    """表上下文"""

    catalog: str
    schema: str
    table: str
    description: str | None = None
    columns: list[dict[str, Any]] | None = None


@dataclass
class MetricDTO:
    """指标（精简版，用于 AI 上下文）"""

    code: str
    name: str | None = None
    description: str | None = None


@dataclass
class MetricContextDTO:
    """指标上下文（完整版，用于 AI 填写派生/复合指标）"""

    code: str
    name: str | None = None
    description: str | None = None
    metric_type: str | None = None  # AtomicMetric / DerivedMetric / CompositeMetric
    unit: str | None = None
    calculation_formula: str | None = None
    aggregation_logic: str | None = None
