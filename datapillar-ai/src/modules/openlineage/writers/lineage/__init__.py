"""
OpenLineage LineageWriter 子模块（仅写关系）

约定：
- LineageWriter 只做"路由/编排/统计"
- 结构关系（HAS_*）与血缘关系（INPUT_OF/OUTPUT_TO/DERIVES_FROM/...）统一归属 lineage
- 子 writer 不继承 BaseWriter：它们是可组合的内部组件
"""

from src.modules.openlineage.writers.lineage.column_lineage_writer import ColumnLineageWriter
from src.modules.openlineage.writers.lineage.hierarchy_writer import HierarchyWriter
from src.modules.openlineage.writers.lineage.metric_column_lineage_writer import (
    MetricColumnLineageWriter,
)
from src.modules.openlineage.writers.lineage.metric_relationship_writer import (
    MetricRelationshipWriter,
)
from src.modules.openlineage.writers.lineage.sql_writer import SQLWriter
from src.modules.openlineage.writers.lineage.table_lineage_writer import TableLineageWriter
from src.modules.openlineage.writers.lineage.tag_relationship_writer import TagRelationshipWriter
from src.modules.openlineage.writers.lineage.valuedomain_lineage_writer import (
    ValueDomainLineageWriter,
)

__all__ = [
    "ColumnLineageWriter",
    "HierarchyWriter",
    "MetricColumnLineageWriter",
    "MetricRelationshipWriter",
    "SQLWriter",
    "TableLineageWriter",
    "TagRelationshipWriter",
    "ValueDomainLineageWriter",
]
