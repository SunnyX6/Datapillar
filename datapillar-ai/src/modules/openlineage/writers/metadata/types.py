from __future__ import annotations

from collections.abc import Awaitable, Callable

# 通用 embedding 回调：(node_id, node_label, name, description, tags)
QueueEmbeddingTask = Callable[[str, str, str, str | None, list[str] | None], Awaitable[None]]

# Tag 专用 embedding 回调：(node_id, text) - Tag name 本身有业务含义，不需要 description 必选
QueueTagEmbeddingTask = Callable[[str, str], Awaitable[None]]
