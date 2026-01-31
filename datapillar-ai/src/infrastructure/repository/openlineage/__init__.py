# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage 模块专用数据访问层

约定：
- `src/infrastructure/repository/` 根目录只放跨模块可复用的通用数据访问
- `src/infrastructure/repository/<module>/` 目录放某个模块专用的数据访问（例如 openlineage）
"""

from src.infrastructure.repository.openlineage.lineage import Lineage
from src.infrastructure.repository.openlineage.metadata import Metadata

__all__ = [
    "Metadata",
    "Lineage",
]
