# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Experience retriever.

Responsibilities:
1. Retrieve similar experiences
2. Compose context automatically (via ExperienceRecord.to_context())

Example:
```python
from datapillar_oneagentic.experience import ExperienceRetriever

retriever = ExperienceRetriever(store=store, embedding_provider=embedding_provider)

# Retrieve similar experiences
records = await retriever.search(goal="Analyze sales data", k=5)

# Build context (used internally by the framework)
context = await retriever.build_context(goal="Analyze sales data")
```
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from datapillar_oneagentic.utils.prompt_format import format_markdown

if TYPE_CHECKING:
    from datapillar_oneagentic.experience.learner import ExperienceRecord
    from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore
    from datapillar_oneagentic.providers.llm.embedding import EmbeddingProvider

logger = logging.getLogger(__name__)


class ExperienceRetriever:
    """
    Experience retriever.

    Responsibilities:
    1. Retrieve similar experiences from the vector store
    2. Assemble context that can be injected into prompts
    """

    def __init__(self, store: ExperienceStore, embedding_provider: "EmbeddingProvider"):
        """
        Initialize the retriever.

        Args:
            store: experience store (ExperienceStore interface)
            embedding_provider: embedding provider for vectorization
        """
        self._store = store
        self._embedding_provider = embedding_provider

    async def search(
        self,
        goal: str,
        k: int = 5,
        outcome: str | None = None,
    ) -> list[ExperienceRecord]:
        """
        Retrieve similar experiences.

        Args:
            goal: current task goal
            k: number of results to return
            outcome: outcome filter (success / failure / None=all)

        Returns:
            A list of experience records.
        """
        # Embed the query text.
        try:
            query_vector = await self._embedding_provider.embed_text(goal)
        except Exception as e:
            logger.warning(f"Query embedding failed: {e}")
            return []

        # Call store.search directly and return ExperienceRecord list.
        return await self._store.search(query_vector, k=k, outcome=outcome)

    async def build_context(
        self,
        goal: str,
        k: int = 3,
        prefer_success: bool = True,
    ) -> str:
        """
        Build experience context.

        This is used internally to inject similar experiences into prompts.

        Args:
            goal: current task goal
            k: number of experiences to include
            prefer_success: whether to prioritize successful cases

        Returns:
            Context string; empty string if no records found.
        """
        if prefer_success:
            # Fetch successful cases first.
            records = await self.search(goal, k=k, outcome="success")
            # Fill the remainder with any outcomes if needed.
            if len(records) < k:
                more = await self.search(goal, k=k - len(records), outcome=None)
                # De-duplicate.
                existing_ids = {r.id for r in records}
                for r in more:
                    if r.id not in existing_ids:
                        records.append(r)
        else:
            records = await self.search(goal, k=k)

        if not records:
            return ""

        # Assemble the context.
        lines: list[str] = []

        for i, record in enumerate(records[:k], 1):
            lines.append(f"### Experience {i}")
            lines.append(record.to_context())
            lines.append("")

        body = "\n".join(lines).strip()
        return format_markdown(
            title="Experience Context",
            sections=[("Similar Experiences", body)],
        )

    async def get_common_tools(self, goal: str, k: int = 10) -> list[str]:
        """
        Get commonly used tools.

        Args:
            goal: task goal
            k: number of experiences to inspect

        Returns:
            A list of tools, sorted by usage frequency.
        """
        records = await self.search(goal, k=k, outcome="success")

        tool_counts: dict[str, int] = {}
        for record in records:
            for tool in record.tools_used:
                tool_counts[tool] = tool_counts.get(tool, 0) + 1

        sorted_tools = sorted(tool_counts.keys(), key=lambda t: tool_counts[t], reverse=True)
        return sorted_tools
