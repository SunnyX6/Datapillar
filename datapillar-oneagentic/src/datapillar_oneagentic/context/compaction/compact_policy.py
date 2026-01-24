"""
压缩策略配置

定义压缩的保留规则、摘要模板等。
配置由调用方显式传入。
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from datapillar_oneagentic.utils.prompt_format import format_markdown


class CompactPolicy(BaseModel):
    """
    压缩策略配置

    控制如何压缩。
    默认值由调用方传入，也可手动覆盖。
    """

    min_keep_entries: int | None = Field(
        default=None,
        ge=1,
        description="最少保留的消息数",
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
        description="压缩提示词模板",
    )

    def get_min_keep_entries(self) -> int:
        """获取最少保留消息数"""
        if self.min_keep_entries is None:
            raise ValueError("compact_min_keep_entries 未配置")
        return self.min_keep_entries


class CompactResult(BaseModel):
    """压缩结果"""

    success: bool = Field(..., description="是否成功")
    summary: str = Field(default="", description="压缩后的摘要")
    kept_count: int = Field(default=0, description="保留的消息数")
    removed_count: int = Field(default=0, description="移除的消息数")
    error: str | None = Field(default=None, description="错误信息")

    @classmethod
    def failed(cls, error: str) -> CompactResult:
        """创建失败结果"""
        return cls(success=False, error=error)

    @classmethod
    def no_action(cls, reason: str = "无需压缩") -> CompactResult:
        """创建无操作结果"""
        return cls(success=True, error=reason)
