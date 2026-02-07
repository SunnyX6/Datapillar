# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OpenLineage 元数据数据访问（转发到 Knowledge Repository）
"""

from src.infrastructure.repository.knowledge.sync_metadata import Metadata, TableUpsertPayload

__all__ = ["Metadata", "TableUpsertPayload"]
