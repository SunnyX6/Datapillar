"""
Deliverable Stores - Agent 交付物存储

提供 Agent 交付物的存储：
- InMemoryDeliverableStore: 内存（开发/测试）
- PostgresDeliverableStore: PostgreSQL（生产环境）
- RedisDeliverableStore: Redis（生产环境，支持分布式）

使用示例：
```python
from datapillar_oneagentic.storage import RedisDeliverableStore

store = RedisDeliverableStore(url="redis://localhost:6379")

team = Datapillar(
    name="分析团队",
    agents=[...],
    deliverable_store=store,
)
```
"""

from datapillar_oneagentic.storage.deliverable_stores.memory import InMemoryDeliverableStore
from datapillar_oneagentic.storage.deliverable_stores.postgres import PostgresDeliverableStore
from datapillar_oneagentic.storage.deliverable_stores.redis import RedisDeliverableStore

__all__ = [
    "InMemoryDeliverableStore",
    "PostgresDeliverableStore",
    "RedisDeliverableStore",
]
