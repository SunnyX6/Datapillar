"""
Context Compaction 子模块

上下文压缩，当 messages token 超过阈值时自动压缩历史消息。

核心组件：
- Compactor: 压缩器，直接操作 list[BaseMessage]
- CompactPolicy: 压缩策略配置
- CompactResult: 压缩结果

使用示例：
```python
from datapillar_oneagentic.context.compaction import get_compactor

compactor = get_compactor()

# 检查是否需要压缩
if compactor.needs_compact(messages):
    compressed_messages, result = await compactor.compact(messages)
```
"""

from datapillar_oneagentic.context.compaction.compact_policy import (
    CompactPolicy,
    CompactResult,
)
from datapillar_oneagentic.context.compaction.compactor import (
    Compactor,
    get_compactor,
    clear_compactor_cache,
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
