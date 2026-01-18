"""
ReAct - 规划-执行-反思 循环

实现智能体自主规划能力：
1. Planner: 将目标分解为任务
2. Executor: 执行任务（现有 Agent 逻辑）
3. Reflector: 评估结果，决定下一步

使用示例：
```python
from datapillar_oneagentic.core.graphs.react import Plan, create_plan, reflect

# 规划
plan = await create_plan(goal="...", llm=llm, available_agents=agent_specs)

# 反思
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
