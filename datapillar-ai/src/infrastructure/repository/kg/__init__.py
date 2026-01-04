"""
知识图谱数据访问层（KG Repository）

定位：
- 这是 Neo4j 上“知识图谱/数仓资产”的统一数据访问层
- 供前端知识图谱查询（src/modules/knowledge）与 ETL 多智能体工具（src/modules/etl/tools）共同复用

边界：
- 这里只做数据访问（Cypher 查询/检索/写回），不承载业务编排
"""

from src.infrastructure.repository.kg.dto import (
    MetricDTO,
    ModifierDTO,
    TableContextDTO,
    UnitDTO,
    WordRootDTO,
)
from src.infrastructure.repository.kg.repository import Neo4jKGRepository
from src.infrastructure.repository.kg.writeback import Neo4jKGWritebackRepository

__all__ = [
    "Neo4jKGRepository",
    "Neo4jKGWritebackRepository",
    "WordRootDTO",
    "ModifierDTO",
    "UnitDTO",
    "TableContextDTO",
    "MetricDTO",
]
