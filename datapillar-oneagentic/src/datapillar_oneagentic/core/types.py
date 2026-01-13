"""
核心类型定义

对外暴露：
- Clarification: 需要用户澄清时返回

框架内部：
- AgentResult: Agent 执行结果
"""

from __future__ import annotations

from typing import Any, Literal

from langchain_core.messages import BaseMessage
from pydantic import BaseModel, Field


class Clarification(BaseModel):
    """
    需要用户澄清

    当 Agent 需要用户提供更多信息时返回此类型。
    框架会暂停流程，等待用户回复后重新执行 Agent 的 run() 方法。

    使用示例：
    ```python
    async def run(self, ctx: AgentContext) -> AnalysisOutput | Clarification:
        output = await ctx.get_output(messages)

        if output.confidence < 0.7:
            return ctx.clarify(
                message="需求不够明确",
                questions=output.ambiguities,
            )

        return output
    ```
    """

    message: str = Field(..., description="提示信息")
    questions: list[str] = Field(default_factory=list, description="需要回答的问题")
    options: list[dict[str, Any]] = Field(default_factory=list, description="可选项")


# ==================== 框架内部类型 ====================

AgentResultStatus = Literal["completed", "needs_clarification", "failed", "error"]


class AgentResult(BaseModel):
    """
    Agent 执行结果（框架内部使用）

    业务侧不需要直接构建此类型，框架会自动处理。

    状态语义：
    - completed: Agent 正确完成任务
    - needs_clarification: 需要用户澄清
    - failed: 业务失败（逻辑错误，非技术故障）
    - error: 系统异常（技术故障）
    """

    model_config = {"arbitrary_types_allowed": True}

    status: AgentResultStatus = Field(..., description="执行状态")
    deliverable: Any | None = Field(None, description="交付物")
    deliverable_type: str | None = Field(None, description="交付物类型")
    clarification: Clarification | None = Field(None, description="澄清请求")
    error: str | None = Field(None, description="错误信息")
    messages: list[BaseMessage] = Field(default_factory=list, description="Agent 执行过程中的消息")

    @classmethod
    def completed(
        cls,
        deliverable: Any,
        deliverable_type: str,
        messages: list[BaseMessage] | None = None,
    ) -> AgentResult:
        """创建成功结果"""
        return cls(
            status="completed",
            deliverable=deliverable,
            deliverable_type=deliverable_type,
            messages=messages or [],
        )

    @classmethod
    def needs_clarification(cls, clarification: Clarification) -> AgentResult:
        """创建需要澄清的结果"""
        return cls(
            status="needs_clarification",
            clarification=clarification,
        )

    @classmethod
    def failed(cls, error: str) -> AgentResult:
        """创建业务失败结果"""
        return cls(status="failed", error=error)

    @classmethod
    def system_error(cls, error: str) -> AgentResult:
        """创建系统异常结果"""
        return cls(status="error", error=error)
