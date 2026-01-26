"""
ExperienceStore abstract interface.

All vector databases implement this interface; data uses ExperienceRecord.

Implementations:
- VectorExperienceStore (VectorStore-based implementation)

Example:
```python
from datapillar_oneagentic.storage import create_learning_store
from datapillar_oneagentic.storage.config import VectorStoreConfig
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig

embedding_config = EmbeddingConfig(model="text-embedding-3-small", api_key="sk-xxx")
store = create_learning_store(
    namespace="my_app",
    vector_store_config=VectorStoreConfig(type="lance", path="./data/vectors"),
    embedding_config=embedding_config,
)
```
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from datapillar_oneagentic.experience.learner import ExperienceRecord

logger = logging.getLogger(__name__)


class ExperienceStore(ABC):
    """
    Experience storage interface.

    All vector databases implement this interface with ExperienceRecord schema.
    Switching vector stores requires no business code changes.
    """

    @abstractmethod
    async def initialize(self) -> None:
        """Initialize storage (create tables/indexes)."""
        pass

    @abstractmethod
    async def close(self) -> None:
        """Close storage connection."""
        pass

    # ==================== Write operations ====================

    @abstractmethod
    async def add(self, record: ExperienceRecord) -> str:
        """
        Add an experience record.

        Args:
            record: Experience record (must include vector)

        Returns:
            Record ID
        """
        pass

    @abstractmethod
    async def delete(self, record_id: str) -> bool:
        """
        Delete a record.

        Args:
            record_id: Record ID

        Returns:
            Whether deletion succeeded
        """
        pass

    # ==================== Read operations ====================

    @abstractmethod
    async def get(self, record_id: str) -> ExperienceRecord | None:
        """
        Get a record.

        Args:
            record_id: Record ID

        Returns:
            Experience record or None if not found
        """
        pass

    @abstractmethod
    async def search(
        self,
        query_vector: list[float],
        k: int = 5,
        outcome: str | None = None,
    ) -> list[ExperienceRecord]:
        """
        Vector similarity search.

        Args:
            query_vector: Query vector
            k: Result count
            outcome: Filter (success / failure / None=all)

        Returns:
            Experience records sorted by similarity
        """
        pass

    # ==================== Stats ====================

    @abstractmethod
    async def count(self) -> int:
        """
        Count records.

        Returns:
            Count
        """
        pass
