# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

from dataclasses import dataclass

import logging

from src.infrastructure.repository.knowledge.dto import generate_id
from src.modules.openlineage.parsers.common.namespace import (
    dataset_table_name,
    parse_gravitino_namespace,
)
from src.modules.openlineage.parsers.common.qualified_name import parse_schema_table
from src.modules.openlineage.schemas.events import InputDataset, OutputDataset

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ParsedNamespace:
    metalake: str | None = None
    catalog: str | None = None


@dataclass(frozen=True)
class TableInfo:
    metalake: str
    catalog: str
    schema: str
    table: str
    physical_path: str = ""

    @property
    def id(self) -> str:
        return generate_id("table", self.metalake, self.catalog, self.schema, self.table)

    def column_id(self, column_name: str) -> str:
        return generate_id(
            "column", self.metalake, self.catalog, self.schema, self.table, column_name
        )


class DatasetResolver:
    """
    Dataset 解析工具（供 parser 层复用）

    只负责把 OpenLineage Dataset/job namespace 解析成统一的 TableInfo/ID。
    """

    def parse_job_namespace(self, job_namespace: str) -> ParsedNamespace:
        parsed = parse_gravitino_namespace(job_namespace)
        if parsed and parsed[1]:
            return ParsedNamespace(metalake=parsed[0], catalog=parsed[1])
        return ParsedNamespace()

    def extract_table_info(
        self,
        dataset: InputDataset | OutputDataset,
        *,
        job_namespace: str,
    ) -> TableInfo | None:
        parsed = self.parse_job_namespace(job_namespace)
        if not parsed.metalake or not parsed.catalog:
            return None

        table_name = dataset_table_name(dataset.namespace, dataset.name, dataset.facets)
        if not table_name:
            logger.debug(
                "cannot_extract_table_info",
                extra={"data": {"namespace": dataset.namespace, "name": dataset.name}},
            )
            return None

        parsed_table = parse_schema_table(table_name)
        if not parsed_table:
            logger.debug(
                "invalid_table_name_format",
                extra={"data": {"table_name": table_name}},
            )
            return None

        schema_name, table_only = parsed_table
        return TableInfo(
            metalake=parsed.metalake,
            catalog=parsed.catalog,
            schema=schema_name,
            table=table_only,
            physical_path=dataset.name,
        )

    def path_table_map(
        self,
        datasets: list[InputDataset],
        *,
        job_namespace: str,
    ) -> dict[str, TableInfo]:
        mapping: dict[str, TableInfo] = {}
        for ds in datasets:
            table_info = self.extract_table_info(ds, job_namespace=job_namespace)
            if table_info:
                mapping[ds.name] = table_info
                logical_name = dataset_table_name(ds.namespace, ds.name, ds.facets)
                if logical_name:
                    mapping.setdefault(logical_name, table_info)
        return mapping
