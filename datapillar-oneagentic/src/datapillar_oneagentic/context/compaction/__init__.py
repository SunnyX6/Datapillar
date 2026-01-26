"""
Context compaction submodule.

Compaction is triggered by LLM context overflow.

Core components:
- Compactor: performs compaction on Messages
- CompactPolicy: compaction policy configuration
- CompactResult: compaction result

Example:
```python
from datapillar_oneagentic.context.compaction import get_compactor
from datapillar_oneagentic.exception import LLMError, LLMErrorCategory

compactor = get_compactor(llm=llm)

try:
    result = await llm.ainvoke(messages)
except LLMError as exc:
    if exc.category == LLMErrorCategory.CONTEXT:
        compressed_messages, result = await compactor.compact(messages)
        # Retry with compressed messages
```
"""

from datapillar_oneagentic.context.compaction.compact_policy import (
    CompactPolicy,
    CompactResult,
)
from datapillar_oneagentic.context.compaction.compactor import (
    Compactor,
    get_compactor,
)

__all__ = [
    # Compactor
    "Compactor",
    "get_compactor",
    # Policy and result
    "CompactPolicy",
    "CompactResult",
]
