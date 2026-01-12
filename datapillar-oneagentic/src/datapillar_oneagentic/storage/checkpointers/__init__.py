"""
Checkpointers - Agent 执行状态持久化

提供多种 Checkpointer 实现，使用方按需选择：
- MemoryCheckpointer: 内存（开发/测试）
- SqliteCheckpointer: SQLite（本地持久化）
- PostgresCheckpointer: PostgreSQL（生产环境）
- RedisCheckpointer: Redis（生产环境，支持分布式）

使用示例：
```python
from datapillar_oneagentic.storage import RedisCheckpointer, Datapillar

checkpointer = RedisCheckpointer(url="redis://localhost:6379")

team = Datapillar(
    name="分析团队",
    agents=[...],
    checkpointer=checkpointer,
)
```
"""

from datapillar_oneagentic.storage.checkpointers.memory import MemoryCheckpointer
from datapillar_oneagentic.storage.checkpointers.sqlite import SqliteCheckpointer
from datapillar_oneagentic.storage.checkpointers.postgres import PostgresCheckpointer
from datapillar_oneagentic.storage.checkpointers.redis import RedisCheckpointer

__all__ = [
    "MemoryCheckpointer",
    "SqliteCheckpointer",
    "PostgresCheckpointer",
    "RedisCheckpointer",
]
