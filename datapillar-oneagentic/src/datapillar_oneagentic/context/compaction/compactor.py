"""
Context compactor.

Operates directly on LangGraph messages and compresses history into a summary.

Compaction flow:
1. Keep the most recent N messages plus user/system messages
2. Compress the rest into a summary
3. Return the compacted message list

Triggered by LLM context overflow; no proactive token checks.
"""

from __future__ import annotations

import logging
from typing import Any

from datapillar_oneagentic.context.builder import ContextBuilder
from datapillar_oneagentic.utils.prompt_format import format_markdown
from datapillar_oneagentic.messages import Message, Messages

from datapillar_oneagentic.context.compaction.compact_policy import CompactPolicy, CompactResult

logger = logging.getLogger(__name__)


class Compactor:
    """
    Context compactor.

Operates directly on LangGraph messages:
- Calls the LLM to generate a summary
- Returns compacted messages
    """

    def __init__(self, llm: Any, policy: CompactPolicy | None = None):
        """
        Initialize the compactor.

        Args:
            llm: LLM instance
            policy: Compaction policy
        """
        self.llm = llm
        self.policy = policy or CompactPolicy()

    async def compact(
        self,
        messages: Messages,
    ) -> tuple[Messages, CompactResult]:
        """
        Execute compaction.

        Args:
            messages: Original message list

        Returns:
            (Compacted messages, CompactResult)
        """
        if not messages:
            return messages, CompactResult.no_action("No messages to compact")

        # Classify messages: keep vs compress.
        keep_messages, compress_messages = self._classify_messages(messages)

        if not compress_messages:
            return messages, CompactResult.no_action("No messages to compact")

        # Generate compaction summary.
        try:
            summary = await self._generate_summary(compress_messages)
        except Exception as e:
            logger.error(f"Compaction failed: {e}", exc_info=True)
            return messages, CompactResult.failed(str(e))

        logger.info(
            f"Compaction completed: {len(compress_messages)} -> summary, "
            f"kept {len(keep_messages)}"
        )

        return keep_messages, CompactResult(
            success=True,
            summary=summary,
            kept_count=len(keep_messages),
            removed_count=len(compress_messages),
        )

    def _classify_messages(
        self,
        messages: Messages,
    ) -> tuple[Messages, Messages]:
        """
        Classify messages.

        Rules:
        - Always keep the most recent min_keep_entries
        - Always keep user/system messages
        - Compress the rest
        """
        min_keep = self.policy.get_min_keep()

        if len(messages) <= min_keep:
            return Messages(messages), Messages()

        # Always keep recent messages.
        recent_messages = messages[-min_keep:]
        older_messages = messages[:-min_keep]

        keep_messages = Messages()
        compress_messages = Messages()

        for msg in older_messages:
            # Always keep user/system messages.
            if msg.role in {"user", "system"}:
                keep_messages.append(msg)
            else:
                compress_messages.append(msg)

        # Merge kept and recent messages.
        keep_messages.extend(recent_messages)

        return keep_messages, compress_messages

    async def _generate_summary(self, messages: Messages) -> str:
        """Generate compaction summary."""
        # Build history text.
        history_lines = []
        for msg in messages:
            role = self._get_role_name(msg)
            content = msg.content
            # Truncate overly long messages.
            if len(content) > 500:
                content = content[:500] + "..."
            history_lines.append(f"[{role}] {content}")

        history_text = "\n".join(history_lines)

        # Build compaction prompt.
        prompt = self.policy.compress_prompt_template.format(history=history_text)

        # Call LLM.
        llm_messages = ContextBuilder.build_compactor_messages(
            system_prompt=format_markdown(
                title=None,
                sections=[
                    (
                        "Role",
                        "You are a conversation history compressor that produces a structured summary.",
                    ),
                ],
            ),
            prompt=prompt,
        )

        response = await self.llm.ainvoke(llm_messages)
        summary = response.content.strip()

        return summary

    def _get_role_name(self, msg: Message) -> str:
        """Return a display name for the message role."""
        if msg.role == "user":
            return "User"
        if msg.role == "assistant":
            name = msg.name
            return name if name else "Assistant"
        if msg.role == "tool":
            return f"Tool:{msg.name or 'unknown'}"
        if msg.role == "system":
            return "System"
        return "Unknown"


# === Compactor factory ===


def get_compactor(*, llm: Any, policy: CompactPolicy | None = None) -> Compactor:
    """
    Return a compactor instance.

    Args:
        llm: LLM instance
        policy: Optional compaction policy

    Returns:
        Compactor instance
    """
    return Compactor(llm=llm, policy=policy or CompactPolicy())
