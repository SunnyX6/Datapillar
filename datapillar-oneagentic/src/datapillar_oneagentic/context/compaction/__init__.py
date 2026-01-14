"""
Context Compaction 子模块

上下文压缩，由 ContextLengthExceededError 异常触发。

核心组件：
- Compactor: 压缩器，直接操作 list[BaseMessage]
- CompactPolicy: 压缩策略配置
- CompactResult: 压缩结果

使用示例：
```python
from datapillar_oneagentic.context.compaction import get_compactor
from datapillar_oneagentic.resilience import ContextLengthExceededError

compactor = get_compactor()

try:
    result = await llm.ainvoke(messages)
except ContextLengthExceededError:
    compressed_messages, result = await compactor.compact(messages)
    # 用压缩后的 messages 重试
```
"""

from datapillar_oneagentic.context.compaction.compact_policy import (
    CompactPolicy,
    CompactResult,
)
from datapillar_oneagentic.context.compaction.compactor import (
    Compactor,
    clear_compactor_cache,
    get_compactor,
)

__all__ = [
    # 压缩器
    "Compactor",
    "get_compactor",
    "clear_compactor_cache",
    # 策略和结果
    "CompactPolicy",
    "CompactResult",
]
