"""
数据访问层（Repository）

提供数据库访问的统一接口
"""

from src.infrastructure.repository.model_repository import ModelRepository
from src.infrastructure.repository.component_repository import ComponentRepository
from src.infrastructure.repository.neo4j_kg import (
    Neo4jKGRepository,
    WordRootDTO,
    ModifierDTO,
    UnitDTO,
    TableContextDTO,
    MetricDTO,
)

# 兼容别名
KnowledgeRepository = Neo4jKGRepository
SemanticRepository = Neo4jKGRepository

__all__ = [
    "ModelRepository",
    "ComponentRepository",
    "Neo4jKGRepository",
    # 兼容别名
    "KnowledgeRepository",
    "SemanticRepository",
    # DTOs
    "WordRootDTO",
    "ModifierDTO",
    "UnitDTO",
    "TableContextDTO",
    "MetricDTO",
]
