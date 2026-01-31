# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Context checkpoint submodule - checkpoint management.

Wraps the Checkpointer and provides a unified management interface.
"""

from __future__ import annotations

import logging
import uuid
from typing import TYPE_CHECKING, Any

from datapillar_oneagentic.context.checkpoint.types import CheckpointType
from datapillar_oneagentic.core.types import SessionKey

if TYPE_CHECKING:
    pass

logger = logging.getLogger(__name__)


def _generate_checkpoint_id() -> str:
    """Generate a checkpoint ID."""
    return f"cp_{uuid.uuid4().hex[:12]}"


class CheckpointManager:
    """
    Checkpoint manager.

    Wraps LangGraph Checkpointer and provides:
    - Unified checkpoint creation
    - Checkpoint listing
    - State recovery
    """

    def __init__(
        self,
        *,
        key: SessionKey,
        checkpointer=None,
    ):
        """
        Create a checkpoint manager.

        Args:
            key: SessionKey (namespace + session_id)
            checkpointer: Optional Checkpointer instance
        """
        self._key = key
        self._checkpointer = checkpointer

    @property
    def key(self) -> SessionKey:
        """Return the session key."""
        return self._key

    @property
    def thread_id(self) -> str:
        """Return the thread ID (for LangGraph)."""
        return str(self._key)

    def get_config(self, checkpoint_id: str | None = None) -> dict:
        """Return LangGraph config."""
        config: dict[str, Any] = {
            "configurable": {
                "thread_id": self.thread_id,
            }
        }
        if checkpoint_id:
            config["configurable"]["checkpoint_id"] = checkpoint_id
        return config

    def generate_checkpoint_id(
        self,
        checkpoint_type: CheckpointType = CheckpointType.AUTO,
    ) -> str:
        """Generate a new checkpoint ID."""
        type_prefix = checkpoint_type.value[:3]
        return f"cp_{type_prefix}_{uuid.uuid4().hex[:8]}"

    async def get_state(self, compiled_graph, checkpoint_id: str | None = None) -> dict | None:
        """
        Get state for a checkpoint.

        Args:
            compiled_graph: Compiled LangGraph
            checkpoint_id: Optional checkpoint ID (latest when omitted)

        Returns:
            State dict or None
        """
        config = self.get_config(checkpoint_id)
        try:
            state_snapshot = await compiled_graph.aget_state(config)
            if state_snapshot and state_snapshot.values:
                return dict(state_snapshot.values)
        except Exception as e:
            logger.error(f"Failed to fetch state: {e}")
        return None

    async def get_snapshot(self, compiled_graph, checkpoint_id: str | None = None):
        """
        Get raw state snapshot.

        Args:
            compiled_graph: Compiled LangGraph
            checkpoint_id: Optional checkpoint ID (latest when omitted)
        """
        config = self.get_config(checkpoint_id)
        try:
            return await compiled_graph.aget_state(config)
        except Exception as e:
            logger.error(f"Failed to fetch state snapshot: {e}")
            return None

    async def update_state(
        self,
        compiled_graph,
        updates: dict,
        checkpoint_id: str | None = None,
    ) -> str | None:
        """
        Update state.

        Args:
            compiled_graph: Compiled LangGraph
            updates: State updates
            checkpoint_id: Optional checkpoint ID

        Returns:
            New checkpoint ID or None
        """
        config = self.get_config(checkpoint_id)
        try:
            result = await compiled_graph.aupdate_state(config, updates)
            if result and "configurable" in result:
                return result["configurable"].get("checkpoint_id")
        except Exception as e:
            logger.error(f"Failed to update state: {e}")
        return None

    async def list_checkpoints(self, compiled_graph, limit: int = 20) -> list[dict]:
        """
        List checkpoints.

        Args:
            compiled_graph: Compiled LangGraph
            limit: Maximum number of checkpoints

        Returns:
            List of checkpoints
        """
        config = self.get_config()
        checkpoints = []
        try:
            async for state in compiled_graph.aget_state_history(config):
                checkpoints.append({
                    "checkpoint_id": state.config.get("configurable", {}).get("checkpoint_id"),
                    "created_at": state.created_at,
                    "parent_id": state.parent_config.get("configurable", {}).get("checkpoint_id")
                    if state.parent_config else None,
                })
                if len(checkpoints) >= limit:
                    break
        except Exception as e:
            logger.error(f"Failed to list checkpoints: {e}")
        return checkpoints

    async def delete(self) -> bool:
        """Delete all checkpoints for the session."""
        if not self._checkpointer:
            return False
        try:
            import asyncio
            import inspect
            delete_method = getattr(self._checkpointer, "delete_thread", None)
            if delete_method is None:
                return False
            if asyncio.iscoroutinefunction(delete_method) or inspect.isasyncgenfunction(delete_method):
                await delete_method(self.thread_id)
            else:
                delete_method(self.thread_id)
            return True
        except Exception as e:
            logger.error(f"Failed to delete checkpoints: {e}")
            return False
