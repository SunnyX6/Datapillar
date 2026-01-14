"""
Agent 工作清单（TodoList）

职责：
- 记录 Agent 的工作步骤（我要做什么）
- 跟踪每个步骤的状态（做到哪了）

设计原则：
- 状态只在步骤级别，整体状态从步骤推导
- Agent 自己拆解步骤，不依赖外部需求ID
"""

from __future__ import annotations

import time
from typing import Literal

from pydantic import BaseModel, Field

StepStatus = Literal["pending", "in_progress", "completed", "skipped", "failed"]


def _now_ms() -> int:
    return int(time.time() * 1000)


class WorkStep(BaseModel):
    """
    工作步骤 - Agent 自己拆解的子任务

    字段：
    - id: 步骤ID（s1, s2...）
    - description: 这一步要做什么
    - status: 步骤状态
    - result: 完成后的结果说明
    """

    id: str = Field(..., description="步骤ID")
    description: str = Field(..., description="步骤描述")
    status: StepStatus = Field(default="pending", description="步骤状态")
    result: str | None = Field(default=None, description="完成后的结果说明")

    def mark_in_progress(self) -> None:
        """标记为进行中"""
        self.status = "in_progress"

    def mark_completed(self, result: str | None = None) -> None:
        """标记为完成"""
        self.status = "completed"
        self.result = result

    def mark_skipped(self) -> None:
        """标记为跳过"""
        self.status = "skipped"

    def mark_failed(self, reason: str | None = None) -> None:
        """标记为失败"""
        self.status = "failed"
        self.result = reason


class AgentTodoList(BaseModel):
    """
    Agent 工作清单

    职责：记录工作步骤，跟踪进度。
    整体状态从步骤推导，不单独维护。
    """

    agent_id: str = Field(..., description="Agent ID")
    session_id: str = Field(..., description="会话ID")
    current_task: str | None = Field(default=None, description="当前任务描述")
    steps: list[WorkStep] = Field(default_factory=list, description="工作步骤")
    next_step_id: int = Field(default=1, description="下一个步骤ID")
    updated_at_ms: int = Field(default_factory=_now_ms, description="更新时间")

    def set_task(self, task: str) -> None:
        """设置当前任务"""
        self.current_task = task
        self.updated_at_ms = _now_ms()

    def add_step(self, description: str) -> WorkStep:
        """添加工作步骤"""
        step = WorkStep(
            id=f"s{self.next_step_id}",
            description=description,
        )
        self.steps.append(step)
        self.next_step_id += 1
        self.updated_at_ms = _now_ms()
        return step

    def get_step(self, step_id: str) -> WorkStep | None:
        """获取指定步骤"""
        for step in self.steps:
            if step.id == step_id:
                return step
        return None

    def get_current_step(self) -> WorkStep | None:
        """获取当前进行中的步骤"""
        for step in self.steps:
            if step.status == "in_progress":
                return step
        return None

    def get_next_pending_step(self) -> WorkStep | None:
        """获取下一个待处理的步骤"""
        for step in self.steps:
            if step.status == "pending":
                return step
        return None

    def is_all_done(self) -> bool:
        """是否全部完成（所有步骤都是 completed 或 skipped）"""
        if not self.steps:
            return False
        return all(s.status in ("completed", "skipped") for s in self.steps)

    def has_failed(self) -> bool:
        """是否有失败的步骤"""
        return any(s.status == "failed" for s in self.steps)

    def to_prompt(self) -> str:
        """
        生成给 Agent 看的 prompt

        格式：
        ## 我的工作：xxx
        - [completed] [s1] 步骤1 -> 结果
        - [in_progress] [s2] 步骤2
        - [pending] [s3] 步骤3
        """
        if not self.current_task and not self.steps:
            return ""

        lines = []

        if self.current_task:
            lines.append(f"## 我的工作：{self.current_task}")

        for step in self.steps:
            status_marker = f"[{step.status}]"
            line = f"- {status_marker} [{step.id}] {step.description}"
            if step.result:
                line += f" -> {step.result}"
            lines.append(line)

        return "\n".join(lines)
