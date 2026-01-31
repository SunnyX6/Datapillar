# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
Tag 关系写入器

负责写入 HAS_TAG 关系边
"""

from __future__ import annotations

import logging
from neo4j import AsyncSession

from src.infrastructure.repository.kg.dto import generate_id
from src.infrastructure.repository.openlineage import Lineage
from src.modules.openlineage.parsers.plans.metadata import TagUpdatePlan

logger = logging.getLogger(__name__)


class TagRelationshipWriter:
    """Tag 关系写入器（HAS_TAG）"""

    def __init__(self) -> None:
        self.tag_edges_added = 0
        self.tag_edges_removed = 0

    async def write(self, session: AsyncSession, plans: list[TagUpdatePlan]) -> None:
        """
        写入 HAS_TAG 关系边（associate_tags）

        注意：
        - 值域 tags（vd: 前缀）由 ValueDomainLineageWriter 处理 HAS_VALUE_DOMAIN
        - 这里只处理普通 tags 的 HAS_TAG 关系边
        """
        for plan in plans:
            # 计算需要添加的 Tag ID
            tag_ids_to_add = [
                generate_id("tag", plan.metalake, tag_name) for tag_name in plan.tags_to_add
            ]

            # 计算需要移除的 Tag ID
            tag_ids_to_remove = [
                generate_id("tag", plan.metalake, tag_name) for tag_name in plan.tags_to_remove
            ]

            # 添加 HAS_TAG 关系
            if tag_ids_to_add:
                await Lineage.batch_add_has_tag(
                    session,
                    source_label=plan.node_label,
                    source_id=plan.node_id,
                    tag_ids=tag_ids_to_add,
                )
                self.tag_edges_added += len(tag_ids_to_add)
                logger.info(
                    "has_tag_added",
                    extra={
                        "data": {
                            "node_id": plan.node_id,
                            "object_type": plan.object_type,
                            "tags_added": plan.tags_to_add,
                        }
                    },
                )

            # 移除 HAS_TAG 关系
            if tag_ids_to_remove:
                await Lineage.batch_remove_has_tag(
                    session,
                    source_label=plan.node_label,
                    source_id=plan.node_id,
                    tag_ids=tag_ids_to_remove,
                )
                self.tag_edges_removed += len(tag_ids_to_remove)
                logger.info(
                    "has_tag_removed",
                    extra={
                        "data": {
                            "node_id": plan.node_id,
                            "object_type": plan.object_type,
                            "tags_removed": plan.tags_to_remove,
                        }
                    },
                )
