"""
压缩策略配置

定义压缩的触发条件、目标比例、保留规则等。
配置项从全局 datapillar.context 读取，也可手动覆盖。
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class CompactPolicy(BaseModel):
    """
    压缩策略配置

    控制何时压缩、如何压缩。
    默认值从全局配置读取，也可手动覆盖。
    """

    # === 触发条件（None 时读全局配置）===

    trigger_threshold: float | None = Field(
        default=None,
        ge=0.5,
        le=1.0,
        description="触发压缩的阈值（占上下文窗口比例）",
    )

    context_window: int | None = Field(
        default=None,
        gt=0,
        description="上下文窗口大小（tokens）",
    )

    # === 压缩目标 ===

    target_ratio: float | None = Field(
        default=None,
        ge=0.3,
        le=0.9,
        description="压缩后目标比例",
    )

    min_keep_entries: int | None = Field(
        default=None,
        ge=1,
        description="最少保留的消息数",
    )

    max_summary_tokens: int | None = Field(
        default=None,
        gt=0,
        description="压缩摘要的最大 token 数",
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

    def _get_context_config(self):
        """获取全局上下文配置"""
        from datapillar_oneagentic.config import datapillar
        return datapillar.context

    def get_trigger_threshold(self) -> float:
        """获取触发阈值"""
        if self.trigger_threshold is not None:
            return self.trigger_threshold
        return self._get_context_config().compact_trigger_threshold

    def get_context_window(self) -> int:
        """获取上下文窗口大小"""
        if self.context_window is not None:
            return self.context_window
        return self._get_context_config().window_size

    def get_target_ratio(self) -> float:
        """获取目标比例"""
        if self.target_ratio is not None:
            return self.target_ratio
        return self._get_context_config().compact_target_ratio

    def get_min_keep_entries(self) -> int:
        """获取最少保留消息数"""
        if self.min_keep_entries is not None:
            return self.min_keep_entries
        return self._get_context_config().compact_min_keep_entries

    def get_max_summary_tokens(self) -> int:
        """获取摘要最大 token 数"""
        if self.max_summary_tokens is not None:
            return self.max_summary_tokens
        return self._get_context_config().compact_max_summary_tokens

    def get_trigger_tokens(self) -> int:
        """获取触发压缩的 token 阈值"""
        return int(self.get_context_window() * self.get_trigger_threshold())

    def get_target_tokens(self) -> int:
        """获取压缩后的目标 token 数"""
        return int(self.get_context_window() * self.get_target_ratio())


class CompactResult(BaseModel):
    """压缩结果"""

    success: bool = Field(..., description="是否成功")
    summary: str = Field(default="", description="压缩后的摘要")
    kept_count: int = Field(default=0, description="保留的消息数")
    removed_count: int = Field(default=0, description="移除的消息数")
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
