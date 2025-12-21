"""
Schema Facet 解析器

从 schema facet 解析出 Column 节点
"""

from src.modules.openlineage.parsers.base import BaseFacetParser
from src.modules.openlineage.schemas.events import Dataset, RunEvent
from src.modules.openlineage.schemas.facets import SchemaDatasetFacet
from src.modules.openlineage.schemas.neo4j import ColumnNode


class SchemaFacetParser(BaseFacetParser[ColumnNode]):
    """
    Schema Facet 解析器

    从 dataset.facets.schema 中解析出 Column 节点
    """

    @property
    def facet_name(self) -> str:
        return "schema"

    def can_parse(self, event: RunEvent) -> bool:
        """检查事件是否包含 schema facet"""
        for dataset in event.get_all_datasets():
            if dataset.facets and "schema" in dataset.facets:
                return True
        return False

    def parse(self, event: RunEvent) -> list[ColumnNode]:
        """解析所有数据集的 schema facet"""
        columns: list[ColumnNode] = []

        for dataset in event.get_all_datasets():
            columns.extend(self._parse_dataset(dataset))

        return columns

    def _parse_dataset(self, dataset: Dataset) -> list[ColumnNode]:
        """解析单个数据集的 schema facet"""
        if not dataset.facets or "schema" not in dataset.facets:
            return []

        schema_facet = SchemaDatasetFacet.from_dict(dataset.facets["schema"])
        columns: list[ColumnNode] = []

        for field_info in schema_facet.fields:
            column = ColumnNode(
                table_namespace=dataset.namespace,
                table_name=dataset.name,
                name=field_info.name,
                data_type=field_info.type,
                description=field_info.description,
            )
            columns.append(column)

        return columns
