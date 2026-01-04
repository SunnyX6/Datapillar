"""
Agent 执行结果

所有 Agent 的统一返回类型，Orchestrator 根据结果更新 Blackboard 和 Handover。

设计原则：
- Agent 只负责执行逻辑，不负责更新状态
- Agent 返回结构化结果，Orchestrator 负责存储和协调
- 清晰区分：成功、需澄清、需委派、失败
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

AgentResultStatus = Literal["completed", "needs_clarification", "needs_delegation", "failed"]


class ClarificationRequest(BaseModel):
    """澄清请求（需要用户输入）"""

    message: str = Field(..., description="提示信息")
    questions: list[str] = Field(default_factory=list, description="需要回答的问题")
    options: list[str] = Field(default_factory=list, description="可选项")
    guidance: dict[str, Any] | None = Field(None, description="推荐引导")


class DelegationRequest(BaseModel):
    """委派请求（需要其他 Agent 协助）"""

    target_agent: str = Field(..., description="目标 Agent ID")
    reason: str = Field(..., description="委派原因")
    payload: dict[str, Any] = Field(default_factory=dict, description="传递给目标 Agent 的数据")


class AgentResult(BaseModel):
    """
    Agent 执行结果

    统一的返回类型，包含：
    - status: 执行状态
    - summary: 一句话总结（给 Boss 看）
    - deliverable: 交付物（存入 Handover）
    - clarification: 如果需要澄清，请求内容
    - delegation: 如果需要委派，请求内容
    - error: 如果失败，错误信息
    """

    status: AgentResultStatus = Field(..., description="执行状态")
    summary: str = Field(..., description="一句话总结")
    deliverable: Any | None = Field(
        None, description="交付物（AnalysisResult/Workflow/TestResult 等）"
    )
    deliverable_type: str | None = Field(None, description="交付物类型（analysis/plan/test）")
    clarification: ClarificationRequest | None = Field(None, description="澄清请求")
    delegation: DelegationRequest | None = Field(None, description="委派请求")
    error: str | None = Field(None, description="错误信息")

    @classmethod
    def completed(
        cls,
        summary: str,
        deliverable: Any,
        deliverable_type: str,
    ) -> AgentResult:
        """创建成功结果"""
        return cls(
            status="completed",
            summary=summary,
            deliverable=deliverable,
            deliverable_type=deliverable_type,
        )

    @classmethod
    def needs_clarification(
        cls,
        summary: str,
        message: str,
        questions: list[str],
        options: list[str] | None = None,
        guidance: dict[str, Any] | None = None,
    ) -> AgentResult:
        """创建需要澄清的结果"""
        return cls(
            status="needs_clarification",
            summary=summary,
            clarification=ClarificationRequest(
                message=message,
                questions=questions,
                options=options or [],
                guidance=guidance,
            ),
        )

    @classmethod
    def needs_delegation(
        cls,
        summary: str,
        target_agent: str,
        reason: str,
        payload: dict[str, Any] | None = None,
    ) -> AgentResult:
        """创建需要委派的结果"""
        return cls(
            status="needs_delegation",
            summary=summary,
            delegation=DelegationRequest(
                target_agent=target_agent,
                reason=reason,
                payload=payload or {},
            ),
        )

    @classmethod
    def failed(cls, summary: str, error: str) -> AgentResult:
        """创建失败结果"""
        return cls(
            status="failed",
            summary=summary,
            error=error,
        )
