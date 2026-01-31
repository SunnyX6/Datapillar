# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Experience learner.

Responsibilities:
1. Automatically record execution traces
2. Persist experiences via save_experience (with optional feedback)
3. Framework-managed, no policy required

Example:
```python
from datapillar_oneagentic import Datapillar, DatapillarConfig

config = DatapillarConfig(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"},
    embedding={"api_key": "sk-xxx", "model": "text-embedding-3-small"},
)

team = Datapillar(
    config=config,
    agents=[...],
    enable_learning=True,
)

# Execute a task (framework records automatically)
async for event in team.stream(query="Analyze sales data", session_id="s001"):
    ...

# Save experience (with user feedback)
await team.save_experience(
    session_id="s001",
    feedback={"stars": 5, "comment": "Very helpful"},
)
```
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from datapillar_oneagentic.utils.time import now_ms

if TYPE_CHECKING:
    from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore
    from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider

logger = logging.getLogger(__name__)


@dataclass
class ExperienceRecord:
    """
    Experience record.

    This structure is stored in the vector database with columnar fields.
    The vector field stores embeddings for similarity search.
    """

    id: str
    """Record ID (usually session_id)."""

    namespace: str
    """Namespace for isolating experiences between teams."""

    session_id: str
    """Session ID."""

    goal: str
    """User goal."""

    outcome: str = "pending"
    """Execution outcome: pending / success / failure / partial."""

    result_summary: str = ""
    """Result summary."""

    tools_used: list[str] = field(default_factory=list)
    """Tools used."""

    agents_involved: list[str] = field(default_factory=list)
    """Agents involved."""

    duration_ms: int = 0
    """Execution duration in milliseconds."""

    feedback: dict[str, Any] = field(default_factory=dict)
    """User feedback (structure defined by the caller)."""

    knowledge_refs: list[dict[str, Any]] = field(default_factory=list)
    """Associated knowledge references."""

    created_at: int = field(default_factory=now_ms)
    """Created timestamp."""

    vector: list[float] = field(default_factory=list)
    """Embedding vector for similarity search."""

    def to_embed_text(self) -> str:
        """
        Generate a full text payload for embedding.

        Includes key signals so similarity search captures complete semantics.
        """
        parts = [f"Goal: {self.goal}"]

        if self.outcome and self.outcome != "pending":
            parts.append(f"Outcome: {self.outcome}")

        if self.result_summary:
            parts.append(f"Summary: {self.result_summary}")

        if self.tools_used:
            parts.append(f"Tools: {', '.join(self.tools_used)}")

        if self.agents_involved:
            parts.append(f"Agents: {', '.join(self.agents_involved)}")

        return "\n".join(parts)

    def to_context(self) -> str:
        """
        Build a prompt-injectable context block.

        This is called internally to render experience context.
        """
        lines = [
            f"- Goal: {self.goal}",
            f"- Outcome: {self.outcome}",
        ]

        if self.result_summary:
            lines.append(f"- Summary: {self.result_summary[:100]}")

        if self.tools_used:
            lines.append(f"- Tools: {', '.join(self.tools_used)}")

        if self.agents_involved:
            lines.append(f"- Agents: {', '.join(self.agents_involved)}")

        if self.feedback:
            feedback_str = ", ".join(f"{k}={v}" for k, v in self.feedback.items())
            lines.append(f"- Feedback: {feedback_str}")

        if self.knowledge_refs:
            lines.append(f"- Knowledge References: {len(self.knowledge_refs)}")

        return "\n".join(lines)

    def to_dict(self) -> dict[str, Any]:
        """Serialize to a dict for storage."""
        return {
            "id": self.id,
            "namespace": self.namespace,
            "session_id": self.session_id,
            "goal": self.goal,
            "outcome": self.outcome,
            "result_summary": self.result_summary,
            "tools_used": self.tools_used,
            "agents_involved": self.agents_involved,
            "duration_ms": self.duration_ms,
            "feedback": self.feedback,
            "knowledge_refs": self.knowledge_refs,
            "created_at": self.created_at,
            "vector": self.vector,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> ExperienceRecord:
        """Deserialize from a dict."""
        return cls(
            id=data.get("id", ""),
            namespace=data.get("namespace", ""),
            session_id=data.get("session_id", ""),
            goal=data.get("goal", ""),
            outcome=data.get("outcome", "pending"),
            result_summary=data.get("result_summary", ""),
            tools_used=data.get("tools_used", []),
            agents_involved=data.get("agents_involved", []),
            duration_ms=data.get("duration_ms", 0),
            feedback=data.get("feedback", {}),
            knowledge_refs=data.get("knowledge_refs", []) or [],
            created_at=data.get("created_at", 0),
            vector=data.get("vector", []),
        )


class ExperienceLearner:
    """
    Experience learner.

    Responsibilities:
    1. Auto-record execution (in-memory temporary storage)
    2. Persist to vector store via save_experience
    """

    def __init__(
        self,
        store: ExperienceStore,
        namespace: str,
        embedding_provider: "EmbeddingProvider",
    ):
        """
        Initialize the learner.

        Args:
            store: experience store (ExperienceStore interface)
            namespace: namespace for isolating team experiences
            embedding_provider: embedding provider for vectorization
        """
        self._store = store
        self._namespace = namespace
        self._embedding_provider = embedding_provider
        self._pending: dict[str, ExperienceRecord] = {}  # Temporary records.

    # ==================== Framework internal ====================

    def start_recording(self, session_id: str, goal: str) -> None:
        """Start recording (framework internal)."""
        self._pending[session_id] = ExperienceRecord(
            id=session_id,
            namespace=self._namespace,
            session_id=session_id,
            goal=goal,
        )

    def record_tool(self, session_id: str, tool_name: str) -> None:
        """Record tool usage (framework internal)."""
        record = self._pending.get(session_id)
        if record and tool_name not in record.tools_used:
            record.tools_used.append(tool_name)

    def record_agent(self, session_id: str, agent_id: str) -> None:
        """Record agent participation (framework internal)."""
        record = self._pending.get(session_id)
        if record and agent_id not in record.agents_involved:
            record.agents_involved.append(agent_id)

    def record_knowledge(self, session_id: str, refs: list[dict[str, Any]]) -> None:
        """Record knowledge references (framework internal)."""
        record = self._pending.get(session_id)
        if not record or not refs:
            return
        existing = {r.get("chunk_id") for r in record.knowledge_refs}
        for ref in refs:
            if ref.get("chunk_id") in existing:
                continue
            record.knowledge_refs.append(ref)

    def complete_recording(
        self,
        session_id: str,
        outcome: str,
        result_summary: str = "",
    ) -> None:
        """Complete recording (framework internal)."""
        record = self._pending.get(session_id)
        if not record:
            return

        record.outcome = outcome
        record.result_summary = result_summary
        record.duration_ms = now_ms() - record.created_at

    # ==================== Caller API ====================

    async def save_experience(
        self,
        session_id: str,
        feedback: dict[str, Any] | None = None,
    ) -> bool:
        """
        Save an experience into the vector store.

        The caller triggers this to persist an experience, with optional feedback.

        Args:
            session_id: session ID
            feedback: user feedback (optional)

        Returns:
            True when saved successfully.
        """
        record = self._pending.pop(session_id, None)
        if not record:
            logger.warning(f"No pending record to save: {session_id}")
            return False

        # Attach feedback.
        if feedback:
            record.feedback = feedback

        # Generate embeddings from the full experience text.
        embed_content = record.to_embed_text()
        try:
            record.vector = await self._embedding_provider.embed_text(embed_content)
        except Exception as e:
            logger.error(f"Embedding generation failed; experience not saved: {e}")
            self._pending[session_id] = record
            return False

        # Persist the ExperienceRecord.
        await self._store.add(record)
        logger.info(f"Experience saved: {session_id}")
        return True

    def has_pending(self, session_id: str) -> bool:
        """Return whether there is a pending record."""
        return session_id in self._pending

    def discard(self, session_id: str) -> None:
        """Discard a pending record."""
        if session_id in self._pending:
            del self._pending[session_id]
