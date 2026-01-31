# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Execution mode enum.

Defines team execution strategies.
"""

from enum import Enum


class Process(str, Enum):
    """
    Execution modes.

    Controls how agents collaborate within a team.
    """

    SEQUENTIAL = "sequential"
    """
    Sequential execution.

    Executes agents in order:
    - Agent 1 → Agent 2 → Agent 3 → ...
    - Each output becomes the next agent's context
    - Suitable for clear pipelines (analysis → design → build → review)
    """

    DYNAMIC = "dynamic"
    """
    Dynamic execution.

    Agents decide whether to delegate:
    - Start from the first agent
    - Delegate when the agent cannot finish
    - Delegation constrained by can_delegate_to
    - Suitable for flexible collaboration
    """

    HIERARCHICAL = "hierarchical"
    """
    Hierarchical execution.

    Manager agent coordinates tasks:
    - Manager receives requests and plans
    - Manager assigns tasks to agents
    - Agents report back after completion
    - Manager aggregates or continues
    - Suitable for complex multi-task scenarios
    """

    MAPREDUCE = "mapreduce"
    """
    MapReduce execution.

    Decompose → parallelize → reduce:
    - Planner splits tasks and assigns agents
    - Map phase executes tasks in parallel
    - Reducer aggregates final deliverable (defaults to last agent schema)
    - Suitable for parallelizable work with aggregation
    """

    REACT = "react"
    """
    ReAct mode (plan-execute-reflect).

    Planning and reflection loop:
    - Controller creates a plan from user goals
    - Agents execute tasks per plan
    - Reflect and decide next step (continue/retry/replan/end)
    - Suitable for complex goals with dynamic adjustments
    """
