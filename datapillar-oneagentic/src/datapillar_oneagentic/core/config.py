"""
Core 模块配置

包含 ContextConfig 和 AgentConfig。
"""

from pydantic import BaseModel, Field


class ContextConfig(BaseModel):
    """上下文配置"""

    window_size: int = Field(
        default=200000,
        gt=0,
        description="上下文窗口大小（tokens）",
    )

    compact_trigger_threshold: float = Field(
        default=0.95,
        ge=0.5,
        le=1.0,
        description="触发压缩的阈值（占窗口比例）",
    )

    compact_target_ratio: float = Field(
        default=0.60,
        ge=0.3,
        le=0.9,
        description="压缩后目标比例",
    )

    compact_min_keep_entries: int = Field(
        default=5,
        ge=1,
        description="最少保留的对话条目数",
    )

    compact_max_summary_tokens: int = Field(
        default=2000,
        gt=0,
        description="压缩摘要最大 token 数",
    )

    def get_trigger_tokens(self) -> int:
        """获取触发压缩的 token 阈值"""
        return int(self.window_size * self.compact_trigger_threshold)

    def get_target_tokens(self) -> int:
        """获取压缩后目标 token 数"""
        return int(self.window_size * self.compact_target_ratio)


class AgentConfig(BaseModel):
    """Agent 执行配置"""

    max_steps: int = Field(
        default=25,
        ge=1,
        description="Agent 最大执行步数（每步 = 1 次 LLM 调用 + 可能的工具调用）",
    )

    timeout_seconds: float = Field(
        default=300.0,
        gt=0,
        description="Agent 单次执行超时（秒）",
    )

    tool_timeout_seconds: float = Field(
        default=30.0,
        gt=0,
        description="工具单次调用超时（秒）",
    )
