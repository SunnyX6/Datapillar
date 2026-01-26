"""
MapReduce schema.

Includes:
- Planner output
- Map tasks
- Map results
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind


class MapReduceTaskOutput(BaseModel):
    """Planner LLM output task."""

    description: str = Field(..., description="Task description")
    agent_id: str = Field(..., description="Assigned agent_id")
    input: str = Field(..., description="Task input for the agent")


class MapReducePlannerOutput(BaseModel):
    """Planner LLM output."""

    understanding: str = Field(..., description="Understanding of the user goal")
    tasks: list[MapReduceTaskOutput] = Field(default_factory=list, description="Task list")


class MapReduceTask(BaseModel):
    """Map phase task."""

    id: str = Field(..., description="Task ID (t1, t2, ...)")
    description: str = Field(..., description="Task description")
    agent_id: str = Field(..., description="Assigned agent_id")
    input: str = Field(..., description="Task input for the agent")


class MapReducePlan(BaseModel):
    """MapReduce planning result."""

    goal: str = Field(..., description="User goal")
    understanding: str = Field(..., description="Planner understanding")
    tasks: list[MapReduceTask] = Field(default_factory=list, description="Task list")


class MapReduceResult(BaseModel):
    """Map phase result for Reduce aggregation."""

    task_id: str = Field(..., description="Task ID")
    agent_id: str = Field(..., description="Assigned agent_id")
    description: str = Field(..., description="Task description")
    input: str = Field(..., description="Task input")
    status: ExecutionStatus = Field(..., description="Execution status")
    failure_kind: FailureKind | None = Field(default=None, description="Failure kind (optional)")
    output: dict | None = Field(default=None, description="Task output")
    error: str | None = Field(default=None, description="Error details")
    todo_updates: list[dict] = Field(default_factory=list, description="Todo updates (optional)")
