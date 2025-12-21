"""
Column Lineage Facet 解析器

从 columnLineage facet 解析出列级血缘关系
"""

from src.modules.openlineage.parsers.base import BaseFacetParser
from src.modules.openlineage.schemas.events import OutputDataset, RunEvent
from src.modules.openlineage.schemas.facets import ColumnLineageDatasetFacet
from src.modules.openlineage.schemas.neo4j import ColumnLineage


class ColumnLineageFacetParser(BaseFacetParser[ColumnLineage]):
    """
    Column Lineage Facet 解析器

    从 output_dataset.facets.columnLineage 中解析出列级血缘关系
    """

    @property
    def facet_name(self) -> str:
        return "columnLineage"

    def can_parse(self, event: RunEvent) -> bool:
        """检查事件是否包含 columnLineage facet"""
        for output_ds in event.outputs:
            if output_ds.facets and "columnLineage" in output_ds.facets:
                return True
        return False

    def parse(self, event: RunEvent) -> list[ColumnLineage]:
        """解析所有输出数据集的 columnLineage facet"""
        lineages: list[ColumnLineage] = []

        for output_ds in event.outputs:
            lineages.extend(self._parse_output_dataset(output_ds))

        return lineages

    def _parse_output_dataset(self, output_ds: OutputDataset) -> list[ColumnLineage]:
        """解析单个输出数据集的 columnLineage facet"""
        if not output_ds.facets or "columnLineage" not in output_ds.facets:
            return []

        col_lineage_facet = ColumnLineageDatasetFacet.from_dict(
            output_ds.facets["columnLineage"]
        )

        lineages: list[ColumnLineage] = []

        for output_col_name, lineage_info in col_lineage_facet.fields.items():
            for input_field in lineage_info.inputFields:
                lineage = ColumnLineage(
                    source_namespace=input_field.namespace,
                    source_table=input_field.name,
                    source_column=input_field.field,
                    target_namespace=output_ds.namespace,
                    target_table=output_ds.name,
                    target_column=output_col_name,
                    transformation_description=lineage_info.transformationDescription,
                    transformation_type=lineage_info.transformationType,
                )
                lineages.append(lineage)

        return lineages
