"""
Agent 执行结果

所有 Agent 的统一返回类型，Orchestrator 根据结果更新 Blackboard 和 Handover。

设计原则：
- Agent 只负责执行逻辑，不负责更新状态
- Agent 返回结构化结果，Orchestrator 负责存储和协调
- 状态语义清晰区分：
  - completed: Agent 正确完成了职责，产出了合格的交付物
  - needs_clarification: 需要用户澄清才能继续
  - needs_delegation: 需要其他 Agent 协助
  - failed: Agent 自身执行失败（技术故障：LLM 超时、网络异常、代码 bug 等）

注意：交付物的业务状态（如 ReviewResult.passed）不影响 Agent 状态。
ReviewerAgent 成功完成 review 工作就是 completed，即使 ReviewResult.passed=False。
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator

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

    状态语义：
    - completed: Agent 正确完成了职责，产出了交付物（交付物的业务状态由其自身字段表示）
    - needs_clarification: 需要用户澄清才能继续
    - needs_delegation: 需要其他 Agent 协助
    - failed: Agent 自身执行失败（技术故障），无法产出交付物

    字段说明：
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
        None, description="交付物（AnalysisResult/Workflow/ReviewResult 等）"
    )
    deliverable_type: str | None = Field(None, description="交付物类型（analysis/plan/test）")
    clarification: ClarificationRequest | None = Field(None, description="澄清请求")
    delegation: DelegationRequest | None = Field(None, description="委派请求")
    error: str | None = Field(None, description="错误信息")

    @property
    def message(self) -> str | None:
        """
        兼容字段：历史代码/测试使用 `result.message`。

        约定：
        - needs_clarification: 返回 clarification.message
        - needs_delegation: 返回 delegation.reason
        - 其他状态: None
        """
        if self.clarification is not None:
            return self.clarification.message
        if self.delegation is not None:
            return self.delegation.reason
        return None

    @model_validator(mode="after")
    def _validate_contract(self) -> AgentResult:
        """
        AgentResult 契约校验（跨字段）。

        目标：
        - completed 必须带交付物（deliverable/deliverable_type）
        - needs_clarification 必须带 clarification
        - needs_delegation 必须带 delegation
        - failed 必须带 error
        - deliverable 存在时，deliverable_type 必须存在（否则无法存入 Handover）
        """
        if self.deliverable is not None and not self.deliverable_type:
            raise ValueError("deliverable 不为空时，deliverable_type 不能为空")

        if self.status == "completed":
            if self.deliverable is None:
                raise ValueError("status=completed 时 deliverable 不能为空")
            if not self.deliverable_type:
                raise ValueError("status=completed 时 deliverable_type 不能为空")

        if self.status == "needs_clarification" and self.clarification is None:
            raise ValueError("status=needs_clarification 时 clarification 不能为空")

        if self.status == "needs_delegation" and self.delegation is None:
            raise ValueError("status=needs_delegation 时 delegation 不能为空")

        if self.status == "failed" and not (self.error and self.error.strip()):
            raise ValueError("status=failed 时 error 不能为空")

        return self

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
