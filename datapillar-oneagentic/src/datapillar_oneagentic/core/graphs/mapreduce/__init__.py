# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
MapReduce mode module.

Public API:
- create_mapreduce_plan
- reduce_map_results
- Schema definitions
"""

from datapillar_oneagentic.core.graphs.mapreduce.planner import create_mapreduce_plan
from datapillar_oneagentic.core.graphs.mapreduce.reducer import reduce_map_results
from datapillar_oneagentic.core.graphs.mapreduce.schemas import (
    MapReducePlan,
    MapReducePlannerOutput,
    MapReduceResult,
    MapReduceTask,
    MapReduceTaskOutput,
)

__all__ = [
    "create_mapreduce_plan",
    "reduce_map_results",
    "MapReducePlan",
    "MapReducePlannerOutput",
    "MapReduceTaskOutput",
    "MapReduceTask",
    "MapReduceResult",
]
