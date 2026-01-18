"""
压缩策略配置

定义压缩的保留规则、摘要模板等。
配置由调用方显式传入。
"""

from __future__ import annotations

from pydantic import BaseModel, Field


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
