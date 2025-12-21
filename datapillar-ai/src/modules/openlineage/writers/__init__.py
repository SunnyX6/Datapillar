"""
Neo4j 写入器

负责将 OpenLineage 事件解析后写入 Neo4j 知识图谱

- MetadataWriter: 元数据节点（Catalog -> Schema -> Table -> Column）
- LineageWriter: 血缘关系（SQL、表级血缘、列级血缘）
"""

from src.modules.openlineage.writers.base import BaseWriter, WriterStats
from src.modules.openlineage.writers.lineage_writer import LineageWriter
from src.modules.openlineage.writers.metadata_writer import MetadataWriter

__all__ = [
    "BaseWriter",
    "WriterStats",
    "MetadataWriter",
    "LineageWriter",
]
