"""
Learning Stores - 向量存储

提供多种向量数据库后端：
- LanceVectorStore: LanceDB（默认，嵌入式）
- ChromaVectorStore: Chroma（支持 local/remote）
- MilvusVectorStore: Milvus（支持 local/remote）

使用示例：
```python
from datapillar_oneagentic import Datapillar
from datapillar_oneagentic.storage.learning_stores import (
    LanceVectorStore,
    ChromaVectorStore,
    MilvusVectorStore,
)

# 使用 LanceDB（默认，嵌入式）
team = Datapillar(
    name="分析团队",
    agents=[...],
    learning_store=LanceVectorStore(path="./data/experience"),
)

# 使用 Chroma（本地）
team = Datapillar(
    name="分析团队",
    agents=[...],
    learning_store=ChromaVectorStore(path="./data/chroma"),
)

# 使用 Chroma（远程）
team = Datapillar(
    name="分析团队",
    agents=[...],
    learning_store=ChromaVectorStore(host="localhost", port=8000),
)

# 使用 Milvus（本地 Lite）
team = Datapillar(
    name="分析团队",
    agents=[...],
    learning_store=MilvusVectorStore(uri="./data/milvus.db"),
)

# 使用 Milvus（远程）
team = Datapillar(
    name="分析团队",
    agents=[...],
    learning_store=MilvusVectorStore(uri="http://localhost:19530", token="root:Milvus"),
)
```
"""

from datapillar_oneagentic.storage.learning_stores.base import (
    VectorStore,
    VectorRecord,
    VectorSearchResult,
)
from datapillar_oneagentic.storage.learning_stores.lance import LanceVectorStore
from datapillar_oneagentic.storage.learning_stores.chroma import ChromaVectorStore
from datapillar_oneagentic.storage.learning_stores.milvus import MilvusVectorStore

__all__ = [
    "VectorStore",
    "VectorRecord",
    "VectorSearchResult",
    "LanceVectorStore",
    "ChromaVectorStore",
    "MilvusVectorStore",
]
