"""VectorExperienceStore - experience storage backed by VectorStore."""

from __future__ import annotations

import json
import logging
from typing import Any

from datapillar_oneagentic.experience.learner import ExperienceRecord
from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore
from datapillar_oneagentic.storage.vector_stores import (
    VectorCollectionSchema,
    VectorField,
    VectorFieldType,
    VectorStore,
)

logger = logging.getLogger(__name__)

_EXPERIENCES = "experiences"
_KEY_SEPARATOR = "::"


class VectorExperienceStore(ExperienceStore):
    """Experience storage implementation using VectorStore."""

    def __init__(self, *, vector_store: VectorStore, dimension: int, namespace: str) -> None:
        self._vector_store = vector_store
        self._dimension = dimension
        self._namespace = namespace
        self._register_schema()

    def _register_schema(self) -> None:
        self._vector_store.register_schema(
            VectorCollectionSchema(
                name=_EXPERIENCES,
                primary_key="record_key",
                fields=[
                    VectorField("record_key", VectorFieldType.STRING),
                    VectorField("id", VectorFieldType.STRING),
                    VectorField("namespace", VectorFieldType.STRING),
                    VectorField("session_id", VectorFieldType.STRING),
                    VectorField("goal", VectorFieldType.STRING),
                    VectorField("outcome", VectorFieldType.STRING),
                    VectorField("result_summary", VectorFieldType.STRING),
                    VectorField("tools_used", VectorFieldType.JSON),
                    VectorField("agents_involved", VectorFieldType.JSON),
                    VectorField("duration_ms", VectorFieldType.INT),
                    VectorField("feedback", VectorFieldType.JSON),
                    VectorField("knowledge_refs", VectorFieldType.JSON),
                    VectorField("created_at", VectorFieldType.INT),
                    VectorField("vector", VectorFieldType.VECTOR, dimension=self._dimension),
                ],
            )
        )

    async def initialize(self) -> None:
        await self._vector_store.ensure_collection(self._vector_store.get_schema(_EXPERIENCES))

    async def close(self) -> None:
        # vector_store lifecycle is managed by the caller.
        return None

    async def add(self, record: ExperienceRecord) -> str:
        await self._vector_store.add(_EXPERIENCES, [self._record_to_row(record)])
        return record.id

    async def delete(self, record_id: str) -> bool:
        deleted = await self._vector_store.delete(
            _EXPERIENCES,
            [self._build_key(record_id)],
        )
        return deleted > 0

    async def get(self, record_id: str) -> ExperienceRecord | None:
        rows = await self._vector_store.get(
            _EXPERIENCES,
            [self._build_key(record_id)],
        )
        if not rows:
            return None
        return self._row_to_record(rows[0])

    async def search(
        self,
        query_vector: list[float],
        k: int = 5,
        outcome: str | None = None,
    ) -> list[ExperienceRecord]:
        filters = {"namespace": self._namespace}
        if outcome:
            filters["outcome"] = outcome
        results = await self._vector_store.search(
            _EXPERIENCES,
            query_vector=query_vector,
            k=k,
            filters=filters,
        )
        return [self._row_to_record(item.record) for item in results]

    async def count(self) -> int:
        rows = await self._vector_store.query(
            _EXPERIENCES,
            filters={"namespace": self._namespace},
        )
        return len(rows)

    def _record_to_row(self, record: ExperienceRecord) -> dict[str, Any]:
        return {
            "record_key": self._build_key(record.id),
            "id": record.id,
            "namespace": self._namespace,
            "session_id": record.session_id,
            "goal": record.goal,
            "outcome": record.outcome,
            "result_summary": record.result_summary,
            "tools_used": json.dumps(record.tools_used, ensure_ascii=False),
            "agents_involved": json.dumps(record.agents_involved, ensure_ascii=False),
            "duration_ms": record.duration_ms,
            "feedback": json.dumps(record.feedback, ensure_ascii=False),
            "knowledge_refs": json.dumps(record.knowledge_refs, ensure_ascii=False),
            "created_at": record.created_at,
            "vector": record.vector,
        }

    def _row_to_record(self, row: dict[str, Any]) -> ExperienceRecord:
        return ExperienceRecord(
            id=row.get("id", ""),
            namespace=row.get("namespace", ""),
            session_id=row.get("session_id", ""),
            goal=row.get("goal", ""),
            outcome=row.get("outcome", "pending"),
            result_summary=row.get("result_summary", ""),
            tools_used=_loads_json(row.get("tools_used")),
            agents_involved=_loads_json(row.get("agents_involved")),
            duration_ms=row.get("duration_ms", 0),
            feedback=_loads_json(row.get("feedback")),
            knowledge_refs=_loads_json(row.get("knowledge_refs")),
            created_at=row.get("created_at", 0),
            vector=row.get("vector", []),
        )

    def _build_key(self, record_id: str) -> str:
        return f"{self._namespace}{_KEY_SEPARATOR}{record_id}"


def _loads_json(value: Any) -> list | dict:
    if value is None:
        return []
    if isinstance(value, (list, dict)):
        return value
    if isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return []
    return []
