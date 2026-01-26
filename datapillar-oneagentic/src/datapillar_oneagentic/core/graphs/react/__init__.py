"""
ReAct - plan, execute, reflect loop.

Enables agent planning:
1. Planner: decomposes goals into tasks
2. Executor: executes tasks (existing agent logic)
3. Reflector: evaluates results and decides next steps

Example:
```python
from datapillar_oneagentic.core.graphs.react import Plan, create_plan, reflect

# Plan
plan = await create_plan(goal="...", llm=llm, available_agents=agent_specs)

# Reflect
reflection = await reflect(goal="...", plan=plan, llm=llm)
```
"""

from datapillar_oneagentic.core.graphs.react.controller import react_controller_node
from datapillar_oneagentic.core.graphs.react.planner import create_plan, replan
from datapillar_oneagentic.core.graphs.react.reflector import decide_next_action, reflect
from datapillar_oneagentic.core.graphs.react.schemas import (
    NextAction,
    Plan,
    PlannerOutput,
    PlanStatus,
    PlanTask,
    PlanTaskOutput,
    Reflection,
    ReflectorOutput,
    TaskStatus,
)

__all__ = [
    # Schema
    "Plan",
    "PlanTask",
    "PlanStatus",
    "TaskStatus",
    "Reflection",
    "NextAction",
    "PlannerOutput",
    "PlanTaskOutput",
    "ReflectorOutput",
    # Planner
    "create_plan",
    "replan",
    # Reflector
    "reflect",
    "decide_next_action",
    # Controller
    "react_controller_node",
]
