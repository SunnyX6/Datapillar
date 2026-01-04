"""
OpenLineage MetadataWriter 子模块

约定：
- MetadataWriter 只做“路由/编排/统计/embedding 入队”
- 物理资产写入：Catalog/Schema/Table/Column
- 语义资产写入：Metric/WordRoot/Modifier/Unit/ValueDomain
- Tag 写入：associate_tags（独立资产，含特殊用途与规则）
- 所有关系（HAS_* 结构边、血缘边）统一由 LineageWriter 负责
"""

from src.modules.openlineage.writers.metadata.physical_assets_writer import PhysicalAssetsWriter
from src.modules.openlineage.writers.metadata.semantic_assets_writer import SemanticAssetsWriter
from src.modules.openlineage.writers.metadata.tag_writer import TagWriter
from src.modules.openlineage.writers.metadata.types import QueueEmbeddingTask, QueueTagEmbeddingTask

__all__ = [
    "PhysicalAssetsWriter",
    "QueueEmbeddingTask",
    "QueueTagEmbeddingTask",
    "SemanticAssetsWriter",
    "TagWriter",
]
