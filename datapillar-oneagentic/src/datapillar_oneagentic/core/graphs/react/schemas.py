"""
ReAct Schema - 规划-执行-反思 的数据模型

核心模型：
- PlanTask: 计划中的任务
- Plan: 计划（任务列表 + 状态）
- Reflection: 反思结果
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from datapillar_oneagentic.utils.time import now_ms
from datapillar_oneagentic.core.status import ExecutionStatus, ProcessStage
# ==================== 状态枚举 ====================

TaskStatus = ExecutionStatus
PlanStatus = ExecutionStatus
NextAction = Literal["continue", "retry", "replan", "complete", "fail"]


# ==================== PlanTask ====================


class PlanTask(BaseModel):
    """
    计划中的任务

    由 Planner 生成，分配给具体的 Agent 执行。
    """

    id: str = Field(..., description="任务ID（t1, t2, ...）")
    description: str = Field(..., description="任务描述（要做什么）")
    assigned_agent: str = Field(..., description="分配给哪个 Agent")
    status: TaskStatus = Field(default=ExecutionStatus.PENDING, description="任务状态")
    depends_on: list[str] = Field(default_factory=list, description="依赖的任务 ID")
    result: str | None = Field(default=None, description="执行结果")
    error: str | None = Field(default=None, description="错误信息（如果失败）")

    def is_ready(self, completed_tasks: set[str]) -> bool:
        """判断任务是否可以执行（依赖都完成了）"""
        return all(dep in completed_tasks for dep in self.depends_on)

    def mark_running(self) -> None:
        """标记为进行中"""
        self.status = ExecutionStatus.RUNNING

    def mark_completed(self, result: str) -> None:
        """标记为完成"""
        self.status = ExecutionStatus.COMPLETED
        self.result = result

    def mark_failed(self, error: str) -> None:
        """标记为失败"""
        self.status = ExecutionStatus.FAILED
        self.error = error


# ==================== Plan ====================


class Plan(BaseModel):
    """
    计划

    由 Planner 生成，包含目标和任务列表。
    Executor 按顺序执行任务，Reflector 评估结果。
    """

    goal: str = Field(..., description="用户目标")
    tasks: list[PlanTask] = Field(default_factory=list, description="任务列表")
    status: PlanStatus = Field(default=ExecutionStatus.PENDING, description="计划状态")
    stage: ProcessStage = Field(default=ProcessStage.PLANNING, description="计划阶段")
    current_task_id: str | None = Field(default=None, description="当前执行的任务 ID")
    retry_count: int = Field(default=0, description="重试次数")
    max_retries: int = Field(default=3, description="最大重试次数")
    created_at_ms: int = Field(default_factory=now_ms, description="创建时间")
    updated_at_ms: int = Field(default_factory=now_ms, description="更新时间")

    def add_task(
        self,
        description: str,
        assigned_agent: str,
        depends_on: list[str] | None = None,
    ) -> PlanTask:
        """添加任务"""
        task_id = f"t{len(self.tasks) + 1}"
        task = PlanTask(
            id=task_id,
            description=description,
            assigned_agent=assigned_agent,
            depends_on=depends_on or [],
        )
        self.tasks.append(task)
        self.updated_at_ms = now_ms()
        return task

    def get_task(self, task_id: str) -> PlanTask | None:
        """获取任务"""
        for task in self.tasks:
            if task.id == task_id:
                return task
        return None

    def get_completed_task_ids(self) -> set[str]:
        """获取已完成的任务 ID"""
        return {t.id for t in self.tasks if t.status == ExecutionStatus.COMPLETED}

    def get_next_task(self) -> PlanTask | None:
        """获取下一个可执行的任务"""
        completed = self.get_completed_task_ids()
        for task in self.tasks:
            if task.status == ExecutionStatus.PENDING and task.is_ready(completed):
                return task
        return None

    def get_current_task(self) -> PlanTask | None:
        """获取当前执行中的任务"""
        if not self.current_task_id:
            return None
        return self.get_task(self.current_task_id)

    def is_all_completed(self) -> bool:
        """是否全部完成"""
        return all(
            t.status in (ExecutionStatus.COMPLETED, ExecutionStatus.SKIPPED)
            for t in self.tasks
        )

    def has_failed_task(self) -> bool:
        """是否有失败的任务"""
        return any(t.status == ExecutionStatus.FAILED for t in self.tasks)

    def can_retry(self) -> bool:
        """是否可以重试"""
        return self.retry_count < self.max_retries

    def increment_retry(self) -> None:
        """增加重试次数"""
        self.retry_count += 1
        self.updated_at_ms = now_ms()

    def to_prompt(self) -> str:
        """生成 prompt 格式"""
        lines = [f"## 目标：{self.goal}", "", "## 任务列表："]

        for task in self.tasks:
            status_marker = f"[{task.status.value}]"
            deps = f" (依赖: {', '.join(task.depends_on)})" if task.depends_on else ""
            line = f"- {status_marker} [{task.id}] {task.description} -> {task.assigned_agent}{deps}"

            if task.result:
                line += f"\n  结果: {task.result}"
            if task.error:
                line += f"\n  错误: {task.error}"

            lines.append(line)

        return "\n".join(lines)


# ==================== Reflection ====================


class Reflection(BaseModel):
    """
    反思结果

    由 Reflector 生成，评估执行结果，决定下一步。
    """

    goal_achieved: bool = Field(..., description="目标是否达成")
    confidence: float = Field(..., ge=0.0, le=1.0, description="置信度 0-1")
    summary: str = Field(..., description="评估总结")
    issues: list[str] = Field(default_factory=list, description="发现的问题")
    suggestions: list[str] = Field(default_factory=list, description="改进建议")
    next_action: NextAction = Field(..., description="下一步行动")
    reason: str = Field(..., description="决策理由")

    def should_continue(self) -> bool:
        """是否继续执行"""
        return self.next_action == "continue"

    def should_retry(self) -> bool:
        """是否重试"""
        return self.next_action == "retry"

    def should_replan(self) -> bool:
        """是否重新规划"""
        return self.next_action == "replan"

    def is_complete(self) -> bool:
        """是否完成"""
        return self.next_action == "complete"

    def is_failed(self) -> bool:
        """是否失败"""
        return self.next_action == "fail"


# ==================== LLM 输出 Schema ====================


class PlanTaskOutput(BaseModel):
    """Planner LLM 输出的任务"""

    description: str = Field(..., description="任务描述")
    assigned_agent: str = Field(..., description="分配给哪个 Agent")
    depends_on: list[str] = Field(default_factory=list, description="依赖的任务序号（从1开始）")


class PlannerOutput(BaseModel):
    """Planner LLM 输出"""

    understanding: str = Field(..., description="对用户目标的理解")
    tasks: list[PlanTaskOutput] = Field(..., description="任务列表")


class ReflectorOutput(BaseModel):
    """Reflector LLM 输出"""

    goal_achieved: bool = Field(..., description="目标是否达成")
    confidence: float = Field(..., ge=0.0, le=1.0, description="置信度 0-1")
    summary: str = Field(..., description="评估总结")
    issues: list[str] = Field(default_factory=list, description="发现的问题")
    suggestions: list[str] = Field(default_factory=list, description="改进建议")
    next_action: NextAction = Field(..., description="下一步：continue/retry/replan/complete/fail")
    reason: str = Field(..., description="决策理由")
