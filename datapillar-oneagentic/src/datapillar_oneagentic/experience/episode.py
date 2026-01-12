"""
Experience 数据模型

核心概念：
- Episode: 一次完整的任务执行经历（成功或失败）
- EpisodeStep: 执行过程中的单个步骤
- Outcome: 执行结果（成功/失败/部分成功）

设计原则：
- Episode 记录完整上下文，便于检索和学习
- 不存储敏感数据（密码、token 等）
- 支持结构化检索（按 agent、任务类型、结果等）
"""

from __future__ import annotations

import time
import uuid
from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


def _now_ms() -> int:
    return int(time.time() * 1000)


def _generate_id() -> str:
    return uuid.uuid4().hex[:12]


class Outcome(str, Enum):
    """执行结果"""
    SUCCESS = "success"
    FAILURE = "failure"
    PARTIAL = "partial"


class ValidationStatus(str, Enum):
    """经验验证状态"""
    PENDING = "pending"      # 待验证（默认）
    VERIFIED = "verified"    # 用户确认有价值
    REJECTED = "rejected"    # 用户标记无价值


class EpisodeStep(BaseModel):
    """
    执行步骤

    记录单个 Agent 的执行过程。
    """

    step_id: str = Field(default_factory=_generate_id, description="步骤 ID")
    agent_id: str = Field(..., description="执行的 Agent ID")
    agent_name: str = Field(..., description="Agent 名称")
    task_description: str = Field(..., description="任务描述")

    # 输入输出
    input_summary: str = Field(default="", description="输入摘要")
    output_summary: str = Field(default="", description="输出摘要")

    # 工具调用
    tools_used: list[str] = Field(default_factory=list, description="使用的工具")
    tool_calls_count: int = Field(default=0, description="工具调用次数")

    # 结果
    outcome: Outcome = Field(default=Outcome.SUCCESS, description="步骤结果")
    error_message: str | None = Field(default=None, description="错误信息")

    # 时间
    started_at_ms: int = Field(default_factory=_now_ms, description="开始时间")
    ended_at_ms: int | None = Field(default=None, description="结束时间")
    duration_ms: int | None = Field(default=None, description="耗时")

    def complete(self, outcome: Outcome, output_summary: str = "", error: str | None = None) -> None:
        """标记步骤完成"""
        self.outcome = outcome
        self.output_summary = output_summary
        self.error_message = error
        self.ended_at_ms = _now_ms()
        self.duration_ms = self.ended_at_ms - self.started_at_ms


class Episode(BaseModel):
    """
    经验片段

    记录一次完整的任务执行经历，包括：
    - 用户目标
    - 执行计划
    - 各步骤执行情况
    - 最终结果
    - 经验总结

    用于：
    - 相似任务检索
    - 成功模式学习
    - 失败原因分析
    """

    episode_id: str = Field(default_factory=_generate_id, description="经验 ID")

    # 团队信息
    team_id: str = Field(default="default", description="团队 ID（用于隔离）")

    # 会话信息
    session_id: str = Field(..., description="会话 ID")
    user_id: str = Field(..., description="用户 ID")

    # 任务信息
    goal: str = Field(..., description="用户目标")
    goal_embedding: list[float] | None = Field(default=None, description="目标向量（用于检索）")
    task_type: str = Field(default="general", description="任务类型")
    tags: list[str] = Field(default_factory=list, description="标签")

    # 执行计划
    plan_summary: str = Field(default="", description="计划摘要")
    planned_steps: int = Field(default=0, description="计划步骤数")

    # 执行过程
    steps: list[EpisodeStep] = Field(default_factory=list, description="执行步骤")
    agents_involved: list[str] = Field(default_factory=list, description="参与的 Agent")
    tools_used: list[str] = Field(default_factory=list, description="使用的工具")

    # 结果
    outcome: Outcome = Field(default=Outcome.SUCCESS, description="最终结果")
    result_summary: str = Field(default="", description="结果摘要")
    deliverable_type: str | None = Field(default=None, description="交付物类型")
    deliverable: Any | None = Field(default=None, description="交付物内容（SQL/工作流等）")

    # 反思和学习
    reflection: str = Field(default="", description="反思总结")
    lessons_learned: list[str] = Field(default_factory=list, description="经验教训")
    success_factors: list[str] = Field(default_factory=list, description="成功因素")
    failure_reasons: list[str] = Field(default_factory=list, description="失败原因")

    # 元数据
    created_at_ms: int = Field(default_factory=_now_ms, description="创建时间")
    completed_at_ms: int | None = Field(default=None, description="完成时间")
    duration_ms: int | None = Field(default=None, description="总耗时")

    # 质量指标
    retry_count: int = Field(default=0, description="重试次数")
    replan_count: int = Field(default=0, description="重新规划次数")
    user_satisfaction: float | None = Field(default=None, ge=0, le=1, description="用户满意度")

    # 反馈验证
    validation_status: ValidationStatus = Field(
        default=ValidationStatus.PENDING,
        description="验证状态：pending/verified/rejected",
    )
    feedback_text: str | None = Field(default=None, description="用户反馈文本")
    feedback_at_ms: int | None = Field(default=None, description="反馈时间")

    def add_step(self, step: EpisodeStep) -> None:
        """添加执行步骤"""
        self.steps.append(step)

        # 更新统计
        if step.agent_id not in self.agents_involved:
            self.agents_involved.append(step.agent_id)

        for tool in step.tools_used:
            if tool not in self.tools_used:
                self.tools_used.append(tool)

    def complete(
        self,
        outcome: Outcome,
        result_summary: str = "",
        reflection: str = "",
        lessons: list[str] | None = None,
    ) -> None:
        """标记经验完成"""
        self.outcome = outcome
        self.result_summary = result_summary
        self.reflection = reflection
        self.completed_at_ms = _now_ms()
        self.duration_ms = self.completed_at_ms - self.created_at_ms

        if lessons:
            self.lessons_learned = lessons

        # 根据结果分类经验
        if outcome == Outcome.SUCCESS:
            self._extract_success_factors()
        elif outcome == Outcome.FAILURE:
            self._extract_failure_reasons()

    def _extract_success_factors(self) -> None:
        """提取成功因素"""
        factors = []

        # 分析成功的步骤
        for step in self.steps:
            if step.outcome == Outcome.SUCCESS:
                if step.tools_used:
                    factors.append(f"有效使用工具: {', '.join(step.tools_used)}")

        # 效率指标
        if self.retry_count == 0:
            factors.append("一次成功，无需重试")
        if self.replan_count == 0:
            factors.append("计划执行顺利，无需调整")

        self.success_factors = factors[:5]  # 最多保留 5 个

    def _extract_failure_reasons(self) -> None:
        """提取失败原因"""
        reasons = []

        # 分析失败的步骤
        for step in self.steps:
            if step.outcome == Outcome.FAILURE and step.error_message:
                reasons.append(f"{step.agent_name}: {step.error_message}")

        # 重试/重规划过多
        if self.retry_count >= 3:
            reasons.append(f"重试次数过多: {self.retry_count} 次")
        if self.replan_count >= 2:
            reasons.append(f"多次重新规划: {self.replan_count} 次")

        self.failure_reasons = reasons[:5]

    def to_search_text(self) -> str:
        """生成用于搜索的文本"""
        parts = [
            f"目标: {self.goal}",
            f"类型: {self.task_type}",
            f"结果: {self.outcome.value}",
        ]

        if self.plan_summary:
            parts.append(f"计划: {self.plan_summary}")

        if self.result_summary:
            parts.append(f"结果: {self.result_summary}")

        if self.reflection:
            parts.append(f"反思: {self.reflection}")

        if self.lessons_learned:
            parts.append(f"经验: {'; '.join(self.lessons_learned)}")

        return "\n".join(parts)

    def to_prompt(self) -> str:
        """生成可注入 prompt 的格式"""
        lines = [
            f"## 相似经验: {self.goal[:50]}...",
            f"- 结果: {self.outcome.value}",
            f"- 参与 Agent: {', '.join(self.agents_involved)}",
        ]

        if self.lessons_learned:
            lines.append(f"- 经验教训: {'; '.join(self.lessons_learned[:3])}")

        if self.success_factors:
            lines.append(f"- 成功因素: {'; '.join(self.success_factors[:3])}")

        if self.failure_reasons:
            lines.append(f"- 失败原因: {'; '.join(self.failure_reasons[:3])}")

        return "\n".join(lines)

    def apply_feedback(
        self,
        satisfaction: float,
        feedback_text: str | None = None,
        *,
        auto_verify: bool = True,
        verify_threshold: float = 0.7,
        reject_threshold: float = 0.3,
    ) -> None:
        """
        应用用户反馈

        Args:
            satisfaction: 满意度 0-1
            feedback_text: 反馈文本
            auto_verify: 是否根据满意度自动设置验证状态
            verify_threshold: 满意度超过此值自动 verified
            reject_threshold: 满意度低于此值自动 rejected
        """
        self.user_satisfaction = satisfaction
        self.feedback_text = feedback_text
        self.feedback_at_ms = _now_ms()

        if auto_verify:
            if satisfaction >= verify_threshold:
                self.validation_status = ValidationStatus.VERIFIED
            elif satisfaction <= reject_threshold:
                self.validation_status = ValidationStatus.REJECTED

    def verify(self) -> None:
        """手动标记为已验证"""
        self.validation_status = ValidationStatus.VERIFIED
        if not self.feedback_at_ms:
            self.feedback_at_ms = _now_ms()

    def reject(self, reason: str | None = None) -> None:
        """手动标记为已拒绝"""
        self.validation_status = ValidationStatus.REJECTED
        if reason:
            self.feedback_text = reason
        if not self.feedback_at_ms:
            self.feedback_at_ms = _now_ms()

    @property
    def is_verified(self) -> bool:
        """是否已验证"""
        return self.validation_status == ValidationStatus.VERIFIED

    @property
    def is_rejected(self) -> bool:
        """是否已拒绝"""
        return self.validation_status == ValidationStatus.REJECTED

    @property
    def is_pending(self) -> bool:
        """是否待验证"""
        return self.validation_status == ValidationStatus.PENDING
