"""
Compaction policy configuration.

Defines keep rules, summary templates, and more.
Configurations are provided by callers explicitly.
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from datapillar_oneagentic.utils.prompt_format import format_markdown


class CompactPolicy(BaseModel):
    """
    Compaction policy configuration.

    Controls how compaction works.
    Defaults are provided by callers and can be overridden.
    """

    min_keep_entries: int | None = Field(
        default=None,
        ge=1,
        description="Minimum number of messages to keep",
    )

    compress_prompt_template: str = Field(
        default=format_markdown(
            title="Compression Task",
            sections=[
                (
                    "Instructions",
                    [
                        "Keep: user goal, key decisions, completed work, critical errors.",
                        "Drop: exploration, internal reasoning, repetition.",
                        "Output as structured Markdown.",
                    ],
                ),
                (
                    "Output Sections",
                    [
                        "User Goal",
                        "Completed Work",
                        "Key Decisions",
                        "Open Issues",
                    ],
                ),
                ("Conversation History", "{history}"),
                ("Summary", ""),
            ],
        ),
        description="Compaction prompt template",
    )

    def get_min_keep(self) -> int:
        """Return the minimum number of messages to keep."""
        if self.min_keep_entries is None:
            raise ValueError("compact_min_keep_entries is not configured")
        return self.min_keep_entries


class CompactResult(BaseModel):
    """Compaction result."""

    success: bool = Field(..., description="Whether compaction succeeded")
    summary: str = Field(default="", description="Compaction summary")
    kept_count: int = Field(default=0, description="Number of messages kept")
    removed_count: int = Field(default=0, description="Number of messages removed")
    error: str | None = Field(default=None, description="Error message")

    @classmethod
    def failed(cls, error: str) -> CompactResult:
        """Create a failed result."""
        return cls(success=False, error=error)

    @classmethod
    def no_action(cls, reason: str = "No compaction needed") -> CompactResult:
        """Create a no-op result."""
        return cls(success=True, error=reason)
