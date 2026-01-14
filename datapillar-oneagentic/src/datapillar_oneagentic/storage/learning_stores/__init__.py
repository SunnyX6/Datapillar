"""
Learning Stores - 经验向量存储

提供多种向量数据库后端，统一使用 ExperienceStore 抽象接口。
数据结构统一使用 ExperienceRecord。

实现类：
- LanceExperienceStore: LanceDB（默认，嵌入式）
- ChromaExperienceStore: Chroma（可选）
- MilvusExperienceStore: Milvus（可选）

使用示例：
```python
from datapillar_oneagentic.storage.learning_stores import (
    ExperienceStore,
    LanceExperienceStore,
)

# 使用 LanceDB
store = LanceExperienceStore(
    path="./data/experience",
    namespace="my_app",
)
await store.initialize()

# 所有操作使用 ExperienceRecord
from datapillar_oneagentic.experience import ExperienceRecord
record = ExperienceRecord(...)
await store.add(record)
```
"""

from datapillar_oneagentic.storage.learning_stores.base import ExperienceStore
from datapillar_oneagentic.storage.learning_stores.chroma import ChromaExperienceStore
from datapillar_oneagentic.storage.learning_stores.lance import LanceExperienceStore
from datapillar_oneagentic.storage.learning_stores.milvus import MilvusExperienceStore

__all__ = [
    "ExperienceStore",
    "LanceExperienceStore",
    "ChromaExperienceStore",
    "MilvusExperienceStore",
]
