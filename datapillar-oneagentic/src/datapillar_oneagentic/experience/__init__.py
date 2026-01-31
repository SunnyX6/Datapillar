# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Experience module - learning system.

Responsibilities:
1. Automatically record executions
2. Persist via save_experience (includes feedback)
3. Retrieve similar experiences and compose context

Example:
```python
from datapillar_oneagentic import Datapillar, DatapillarConfig

config = DatapillarConfig(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"},
    embedding={"api_key": "sk-xxx", "model": "text-embedding-3-small"},
)

team = Datapillar(
    config=config,
    agents=[...],
    enable_learning=True,
)

# Execute tasks (framework records automatically)
async for event in team.stream(query="Analyze sales data", session_id="s001"):
    ...

# Save experience (with user feedback)
await team.save_experience(
    session_id="s001",
    feedback={"stars": 5, "comment": "Very helpful"},
)

# No save_experience call = no persistence
```

Data model:
```python
ExperienceRecord:
    id: str               # Record ID
    namespace: str        # Namespace (team isolation)
    session_id: str       # Session ID
    goal: str             # User goal
    outcome: str          # Outcome
    result_summary: str   # Result summary
    tools_used: list      # Tools used
    agents_involved: list # Agents involved
    duration_ms: int      # Duration
    feedback: dict        # User feedback
    created_at: int       # Created time
```
"""

from datapillar_oneagentic.experience.learner import (
    ExperienceLearner,
    ExperienceRecord,
)
from datapillar_oneagentic.experience.retriever import (
    ExperienceRetriever,
)

__all__ = [
    # Data structures
    "ExperienceRecord",
    # Learner
    "ExperienceLearner",
    # Retriever
    "ExperienceRetriever",
]
