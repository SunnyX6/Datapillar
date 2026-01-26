from __future__ import annotations

from collections.abc import Iterable
from typing import Any

from datapillar_oneagentic.messages.models import Message


class Messages(list[Message]):
    """Message sequence (chainable builder + list semantics)."""

    def __init__(self, items: Iterable[Message] | None = None):
        super().__init__()
        if items:
            self.extend(items)

    def append(self, message: Message) -> None:
        if not isinstance(message, Message):
            raise TypeError("Messages only accepts Message instances")
        super().append(message)

    def extend(self, items: Iterable[Message]) -> None:
        for item in items:
            self.append(item)

    def system(self, content: str, **kwargs: Any) -> "Messages":
        self.append(Message.system(content, **kwargs))
        return self

    def user(self, content: str, **kwargs: Any) -> "Messages":
        self.append(Message.user(content, **kwargs))
        return self

    def assistant(self, content: str, **kwargs: Any) -> "Messages":
        self.append(Message.assistant(content, **kwargs))
        return self

    def tool(self, content: str, **kwargs: Any) -> "Messages":
        self.append(Message.tool(content, **kwargs))
        return self
