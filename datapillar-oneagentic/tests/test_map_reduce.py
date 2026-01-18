from __future__ import annotations

import pytest
from pydantic import BaseModel, Field

from datapillar_oneagentic.core.agent import AgentSpec
from datapillar_oneagentic.exception import AgentError, AgentErrorCategory
from datapillar_oneagentic.core.status import ExecutionStatus, FailureKind
from datapillar_oneagentic.core.graphs.mapreduce.planner import create_mapreduce_plan
from datapillar_oneagentic.core.graphs.mapreduce.reducer import reduce_map_results
from datapillar_oneagentic.core.graphs.mapreduce.schemas import (
    MapReducePlan,
    MapReducePlannerOutput,
    MapReduceResult,
    MapReduceTask,
    MapReduceTaskOutput,
)


class _OutputSchema(BaseModel):
    summary: str = Field(...)


class _DummyStructuredLLM:
    def __init__(self, result):
        self._result = result

    async def ainvoke(self, _messages):
        return self._result


class _DummyLLM:
    def __init__(self, result):
        self._result = result

    def with_structured_output(self, _schema, **_kwargs):
        return _DummyStructuredLLM(self._result)


@pytest.mark.asyncio
async def test_create_mapreduce_plan_should_assign_task_ids() -> None:
    output = MapReducePlannerOutput(
        understanding="ok",
        tasks=[
            MapReduceTaskOutput(description="task1", agent_id="a1", input="do1"),
            MapReduceTaskOutput(description="task2", agent_id="a2", input="do2"),
        ],
    )
    llm = _DummyLLM(output)

    agents = [
        AgentSpec(id="a1", name="A1", deliverable_schema=_OutputSchema),
        AgentSpec(id="a2", name="A2", deliverable_schema=_OutputSchema),
    ]

    plan = await create_mapreduce_plan(goal="goal", llm=llm, available_agents=agents)

    assert plan.goal == "goal"
    assert [t.id for t in plan.tasks] == ["t1", "t2"]
    assert [t.agent_id for t in plan.tasks] == ["a1", "a2"]


@pytest.mark.asyncio
async def test_reduce_map_results_should_return_schema_instance() -> None:
    plan = MapReducePlan(
        goal="goal",
        understanding="ok",
        tasks=[
            MapReduceTask(
                id="t1",
                description="task1",
                agent_id="a1",
                input="do1",
            )
        ],
    )
    results = [
        MapReduceResult(
            task_id="t1",
            agent_id="a1",
            description="task1",
            input="do1",
            status=ExecutionStatus.COMPLETED,
            output={"summary": "part"},
        )
    ]

    llm = _DummyLLM(_OutputSchema(summary="final"))
    output = await reduce_map_results(
        plan=plan,
        results=results,
        llm=llm,
        output_schema=_OutputSchema,
    )

    assert isinstance(output, _OutputSchema)
    assert output.summary == "final"


@pytest.mark.asyncio
async def test_reduce_map_results_should_fail_fast_on_failed_map() -> None:
    plan = MapReducePlan(
        goal="goal",
        understanding="ok",
        tasks=[
            MapReduceTask(
                id="t1",
                description="task1",
                agent_id="a1",
                input="do1",
            )
        ],
    )
    results = [
        MapReduceResult(
            task_id="t1",
            agent_id="a1",
            description="task1",
            input="do1",
            status=ExecutionStatus.FAILED,
            failure_kind=FailureKind.SYSTEM,
            error="boom",
        )
    ]

    llm = _DummyLLM(_OutputSchema(summary="final"))
    with pytest.raises(AgentError) as exc_info:
        await reduce_map_results(
            plan=plan,
            results=results,
            llm=llm,
            output_schema=_OutputSchema,
        )

    error = exc_info.value
    assert error.category == AgentErrorCategory.SYSTEM
    assert error.failure_kind == FailureKind.SYSTEM
