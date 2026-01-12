"""
压缩策略配置

定义压缩的触发条件、目标比例、保留规则等。
所有 Agent 共用同一套压缩策略，确保行为一致。

类似 Claude Code 的压缩配置机制。
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

# 对话条目类型
EntryCategory = Literal[
    "user_message",  # 用户消息
    "agent_response",  # Agent 响应
    "agent_handover",  # Agent 交接
    "clarification",  # 澄清对话
    "system_event",  # 系统事件
    "tool_result",  # 工具结果
]

# 默认上下文窗口（200k tokens）
DEFAULT_CONTEXT_WINDOW = 200000


class CompactPolicy(BaseModel):
    """
    压缩策略配置

    控制何时压缩、如何压缩、保留什么。
    """

    # === 触发条件 ===

    trigger_threshold: float = Field(
        default=0.95,
        ge=0.5,
        le=1.0,
        description="触发压缩的阈值（占上下文窗口的比例，默认 95%）",
    )

    context_window: int = Field(
        default=DEFAULT_CONTEXT_WINDOW,
        gt=0,
        description="上下文窗口大小（tokens）",
    )

    # === 压缩目标 ===

    target_ratio: float = Field(
        default=0.60,
        ge=0.3,
        le=0.9,
        description="压缩后目标比例（默认保留 60%）",
    )

    min_keep_entries: int = Field(
        default=5,
        ge=1,
        description="最少保留的对话条目数",
    )

    max_summary_tokens: int = Field(
        default=2000,
        gt=0,
        description="压缩摘要的最大 token 数",
    )

    # === 保留规则 ===

    keep_categories: list[EntryCategory] = Field(
        default_factory=lambda: ["user_message", "clarification"],
        description="保留原文的对话类别（不压缩）",
    )

    compress_categories: list[EntryCategory] = Field(
        default_factory=lambda: ["agent_response", "agent_handover", "tool_result", "system_event"],
        description="需要压缩的对话类别",
    )

    # === 压缩提示 ===

    compress_prompt_template: str = Field(
        default="""请将以下对话历史压缩成结构化摘要。

压缩要求：
1. 保留：用户目标、关键决策、已完成的工作、重要错误信息
2. 丢弃：探索过程、中间思考、冗余解释、重复内容
3. 格式：使用结构化格式，分类整理

输出格式：
## 用户目标
[用户想要完成什么]

## 已完成工作
- [已完成的事项1]
- [已完成的事项2]

## 关键决策
- [重要的技术或业务决策]

## 待解决问题
- [如果有未解决的问题]

对话历史：
{history}

请生成压缩摘要：""",
        description="压缩提示词模板",
    )

    def get_trigger_tokens(self) -> int:
        """获取触发压缩的 token 阈值"""
        return int(self.context_window * self.trigger_threshold)

    def get_target_tokens(self) -> int:
        """获取压缩后的目标 token 数"""
        return int(self.context_window * self.target_ratio)

    def should_keep_entry(self, entry_type: str) -> bool:
        """判断条目是否应该保留原文"""
        return entry_type in self.keep_categories

    def should_compress_entry(self, entry_type: str) -> bool:
        """判断条目是否应该被压缩"""
        return entry_type in self.compress_categories


class CompactResult(BaseModel):
    """压缩结果"""

    success: bool = Field(..., description="是否成功")
    summary: str = Field(default="", description="压缩后的摘要")
    kept_count: int = Field(default=0, description="保留的条目数")
    removed_count: int = Field(default=0, description="移除的条目数")
    tokens_before: int = Field(default=0, description="压缩前 token 数")
    tokens_after: int = Field(default=0, description="压缩后 token 数")
    tokens_saved: int = Field(default=0, description="节省的 token 数")
    error: str | None = Field(default=None, description="错误信息")

    @classmethod
    def failed(cls, error: str) -> CompactResult:
        """创建失败结果"""
        return cls(success=False, error=error)

    @classmethod
    def no_action(cls, reason: str = "无需压缩") -> CompactResult:
        """创建无操作结果"""
        return cls(success=True, error=reason)
