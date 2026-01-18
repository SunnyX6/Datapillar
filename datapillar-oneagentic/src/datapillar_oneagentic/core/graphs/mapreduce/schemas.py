"""
MapReduce 模式 Schema

包含：
- 规划器输出
- Map 任务
- Map 结果
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind


class MapReduceTaskOutput(BaseModel):
    """Planner LLM 输出的任务"""

    description: str = Field(..., description="任务描述")
    agent_id: str = Field(..., description="执行该任务的 Agent ID")
    input: str = Field(..., description="交给 Agent 的具体指令")


class MapReducePlannerOutput(BaseModel):
    """Planner LLM 输出"""

    understanding: str = Field(..., description="对用户目标的理解")
    tasks: list[MapReduceTaskOutput] = Field(default_factory=list, description="任务列表")


class MapReduceTask(BaseModel):
    """Map 阶段的任务"""

    id: str = Field(..., description="任务 ID（t1, t2, ...）")
    description: str = Field(..., description="任务描述")
    agent_id: str = Field(..., description="执行该任务的 Agent ID")
    input: str = Field(..., description="交给 Agent 的具体指令")


class MapReducePlan(BaseModel):
    """MapReduce 规划结果"""

    goal: str = Field(..., description="用户目标")
    understanding: str = Field(..., description="Planner 的理解")
    tasks: list[MapReduceTask] = Field(default_factory=list, description="任务列表")


class MapReduceResult(BaseModel):
    """Map 阶段结果（供 Reduce 聚合）"""

    task_id: str = Field(..., description="任务 ID")
    agent_id: str = Field(..., description="执行任务的 Agent ID")
    description: str = Field(..., description="任务描述")
    input: str = Field(..., description="任务输入")
    status: ExecutionStatus = Field(..., description="执行状态")
    failure_kind: FailureKind | None = Field(default=None, description="失败类型（可选）")
    output: dict | None = Field(default=None, description="任务输出")
    error: str | None = Field(default=None, description="错误信息")
    todo_updates: list[dict] = Field(default_factory=list, description="Todo 更新（可选）")
