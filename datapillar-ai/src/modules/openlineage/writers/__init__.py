"""
Neo4j 写入器

负责将 OpenLineage 事件解析后写入 Neo4j 知识图谱

- MetadataWriter: 元数据节点（Catalog -> Schema -> Table -> Column）
- LineageWriter: 所有关系（HAS_* 结构边 + 血缘边）
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
