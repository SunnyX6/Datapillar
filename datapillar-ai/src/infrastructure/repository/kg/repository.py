"""
Neo4j 知识图谱统一数据访问层（对外入口）

实现拆分目标：
- 标准 Cypher 查询与写回：`kg/queries.py`
- 向量/混合检索：`kg/retrieval.py`
- 写回（命令侧）：`kg/writeback.py`
"""

from __future__ import annotations

from src.infrastructure.repository.kg.dto import (
    MetricDTO,
    ModifierDTO,
    TableContextDTO,
    UnitDTO,
    WordRootDTO,
)
from src.infrastructure.repository.kg.queries import Neo4jKGRepositoryQueries
from src.infrastructure.repository.kg.retrieval import Neo4jKGRepositoryRetrieval


class Neo4jKGRepository(Neo4jKGRepositoryQueries, Neo4jKGRepositoryRetrieval):
    """Neo4j 知识图谱统一数据访问层（组合：查询 + 检索）"""


__all__ = [
    "Neo4jKGRepository",
    "WordRootDTO",
    "ModifierDTO",
    "UnitDTO",
    "TableContextDTO",
    "MetricDTO",
]
