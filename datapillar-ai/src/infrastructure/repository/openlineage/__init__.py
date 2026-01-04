"""
OpenLineage 模块专用 Repository

约定：
- `src/infrastructure/repository/` 根目录只放跨模块可复用的通用 Repository
- `src/infrastructure/repository/<module>/` 目录放某个模块专用的 Repository（例如 openlineage）
"""

from src.infrastructure.repository.openlineage.lineage_repository import (
    OpenLineageLineageRepository,
)
from src.infrastructure.repository.openlineage.metadata_repository import (
    OpenLineageMetadataRepository,
)

__all__ = [
    "OpenLineageMetadataRepository",
    "OpenLineageLineageRepository",
]
