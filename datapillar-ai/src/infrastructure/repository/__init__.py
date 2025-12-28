"""
数据访问层（Repository）

提供数据库访问的统一接口
"""

from src.infrastructure.repository.model_repository import ModelRepository
from src.infrastructure.repository.knowledge_repository import KnowledgeRepository
from src.infrastructure.repository.component_repository import ComponentRepository
from src.infrastructure.repository.semantic_repository import (
    SemanticRepository,
    WordRootDTO,
    ModifierDTO,
    UnitDTO,
)

__all__ = [
    "ModelRepository",
    "KnowledgeRepository",
    "ComponentRepository",
    "SemanticRepository",
    "WordRootDTO",
    "ModifierDTO",
    "UnitDTO",
]
