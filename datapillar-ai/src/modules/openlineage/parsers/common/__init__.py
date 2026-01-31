# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage Parser 通用能力（DRY）

目标：
- 聚合“解析/容错/ID 生成/namespace & dataset 解析”等可复用逻辑
- 供 facets/plans 层复用，避免在多个 parser/writer 中重复实现
"""

from src.modules.openlineage.parsers.common.dataset import (
    DatasetResolver,
    ParsedNamespace,
    TableInfo,
)
from src.modules.openlineage.parsers.common.namespace import (
    dataset_table_name,
    parse_gravitino_namespace,
    symlink_table_name,
)
from src.modules.openlineage.parsers.common.operation import get_operation
from src.modules.openlineage.parsers.common.qualified_name import (
    parse_schema_table,
    parse_table_column,
    split_schema_object,
)

__all__ = [
    "DatasetResolver",
    "ParsedNamespace",
    "TableInfo",
    "parse_gravitino_namespace",
    "symlink_table_name",
    "dataset_table_name",
    "get_operation",
    "split_schema_object",
    "parse_schema_table",
    "parse_table_column",
]
