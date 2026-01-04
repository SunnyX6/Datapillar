"""
OpenLineage Tag 节点写入器

支持操作：
- create_tag / alter_tag：写入 Tag 节点
- drop_tag：删除 Tag 节点

注意：HAS_TAG 关系边由 lineage/tag_relationship_writer.py 处理
"""

from __future__ import annotations

import structlog
from neo4j import AsyncSession

from src.infrastructure.repository.openlineage import OpenLineageMetadataRepository
from src.modules.openlineage.parsers.plans.metadata import TagWritePlan
from src.modules.openlineage.writers.metadata.types import QueueTagEmbeddingTask

logger = structlog.get_logger()


class TagWriter:
    """Tag 节点写入器（仅写节点，不写关系）"""

    TAG_OPERATIONS = {"create_tag", "alter_tag", "drop_tag"}

    def __init__(self, *, queue_tag_embedding_task: QueueTagEmbeddingTask) -> None:
        self._queue_tag_embedding_task = queue_tag_embedding_task
        self._tags_written = 0

    @property
    def tags_written(self) -> int:
        return self._tags_written

    async def write_tags(
        self, session: AsyncSession, plans: list[TagWritePlan], *, created_by: str
    ) -> None:
        """写入 Tag 节点（create_tag / alter_tag）"""
        for plan in plans:
            tag = plan.tag
            await OpenLineageMetadataRepository.upsert_tag(
                session,
                id=tag.id,
                name=tag.name,
                description=tag.description,
                properties=tag.properties,
                created_by=created_by,
            )
            logger.info("tag_upserted", tag_id=tag.id, tag_name=tag.name)

            # Tag 向量化：name 本身有业务含义，可选加 description
            embedding_text = tag.name
            if tag.description:
                embedding_text += f" {tag.description}"
            await self._queue_tag_embedding_task(tag.id, embedding_text)

            self._tags_written += 1

    async def delete_tags(self, session: AsyncSession, tag_ids: list[str]) -> None:
        """删除 Tag 节点（drop_tag）"""
        for tag_id in tag_ids:
            await OpenLineageMetadataRepository.delete_tag(session, tag_id=tag_id)
            logger.info("tag_deleted", tag_id=tag_id)
