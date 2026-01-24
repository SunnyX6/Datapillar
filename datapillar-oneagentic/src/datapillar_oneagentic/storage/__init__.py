"""
Storage 模块 - 统一存储管理

检查点/交付物存储按 namespace 隔离；向量存储固定库名 datapillar，
无数据库概念时通过固定目录/集合前缀体现，表内 namespace 字段负责隔离。

提供上下文管理器工厂函数：
1. create_checkpointer(namespace, agent_config=...) - 创建 LangGraph Checkpointer
2. create_store(namespace, agent_config=...) - 创建 LangGraph Store
3. create_learning_store(namespace, vector_store_config=..., embedding_config=...) - 创建经验向量库
4. create_knowledge_store(namespace, vector_store_config=..., embedding_config=...) - 创建知识库

使用示例：
```python
from datapillar_oneagentic.storage import (
    create_checkpointer,
    create_store,
    create_learning_store,
    create_knowledge_store,
)

# 使用 async with 确保资源正确关闭
namespace = "sales_app"

async with create_checkpointer(namespace, agent_config=config.agent) as checkpointer:
    async with create_store(namespace, agent_config=config.agent) as store:
        graph = builder.compile(checkpointer=checkpointer, store=store)
        # 使用 graph...
# 退出 with 自动关闭连接
```

配置示例（config.toml）：
```toml
[agent.checkpointer]
type = "memory"  # memory | sqlite | postgres | redis
path = "./data/checkpoints"  # sqlite 需要
url = "redis://localhost:6379"  # redis/postgres 需要

[agent.deliverable_store]
type = "memory"  # memory | postgres | redis
url = "redis://localhost:6379"  # redis/postgres 需要

[vector_store]
type = "lance"  # lance | chroma | milvus
path = "./data/vectors"
```
"""

from __future__ import annotations

import logging
import os
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager
from typing import TYPE_CHECKING

from datapillar_oneagentic.core.config import AgentConfig
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig
from datapillar_oneagentic.storage.knowledge_stores.vector import VectorKnowledgeStore
from datapillar_oneagentic.storage.learning_stores.vector import VectorExperienceStore
from datapillar_oneagentic.storage.vector_stores import (
    ChromaVectorStore,
    LanceVectorStore,
    MilvusVectorStore,
    VectorStore,
)

if TYPE_CHECKING:
    from langgraph.checkpoint.base import BaseCheckpointSaver
    from langgraph.store.base import BaseStore

logger = logging.getLogger(__name__)

_memory_checkpointers: dict[str, "BaseCheckpointSaver"] = {}
VECTOR_DB_NAMESPACE = "datapillar"


@asynccontextmanager
async def create_checkpointer(
    namespace: str,
    *,
    agent_config: AgentConfig,
) -> AsyncGenerator[BaseCheckpointSaver, None]:
    """
    根据配置创建 LangGraph Checkpointer 实例

    使用 async with 确保资源正确关闭：
    ```python
    async with create_checkpointer(namespace, agent_config=config.agent) as checkpointer:
        graph = builder.compile(checkpointer=checkpointer)
        # 使用 graph...
    # 自动关闭连接
    ```

    Args:
        namespace: 命名空间（用于数据隔离）
        agent_config: AgentConfig

    Yields:
        LangGraph BaseCheckpointSaver 实例
    """
    config = agent_config.checkpointer
    checkpointer_type = config.type

    if checkpointer_type == "memory":
        from langgraph.checkpoint.memory import MemorySaver
        checkpointer = _memory_checkpointers.get(namespace)
        if checkpointer is None:
            checkpointer = MemorySaver()
            _memory_checkpointers[namespace] = checkpointer
        yield checkpointer

    elif checkpointer_type == "sqlite":
        base_path = config.path or "./data/checkpoints"
        db_path = os.path.join(base_path, f"{namespace}.db")
        os.makedirs(os.path.dirname(db_path), exist_ok=True)

        try:
            from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
        except ImportError as err:
            raise ImportError(
                "使用 sqlite checkpointer 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[sqlite]"
            ) from err
        logger.info(f"创建 sqlite checkpointer: {db_path}")
        async with AsyncSqliteSaver.from_conn_string(db_path) as saver:
            yield saver

    elif checkpointer_type == "postgres":
        if not config.url:
            raise ValueError("postgres checkpointer 需要配置 url")
        try:
            from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        except ImportError as err:
            raise ImportError(
                "使用 postgres checkpointer 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[postgres]"
            ) from err
        logger.info(f"创建 postgres checkpointer, namespace={namespace}")
        async with AsyncPostgresSaver.from_conn_string(config.url) as saver:
            yield saver

    elif checkpointer_type == "redis_shallow":
        if not config.url:
            raise ValueError("redis_shallow checkpointer 需要配置 url")
        try:
            from langgraph.checkpoint.redis.ashallow import AsyncShallowRedisSaver
        except ImportError as err:
            raise ImportError(
                "使用 redis_shallow checkpointer 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[redis]"
            ) from err
        logger.info(f"创建 redis_shallow checkpointer, namespace={namespace}")
        async with AsyncShallowRedisSaver.from_conn_string(config.url) as saver:
            ttl_minutes = config.ttl_minutes
            if ttl_minutes and ttl_minutes > 0:
                saver.ttl = {"default": ttl_minutes}
            yield saver

    elif checkpointer_type == "redis":
        if not config.url:
            raise ValueError("redis checkpointer 需要配置 url")
        try:
            from langgraph.checkpoint.redis.aio import AsyncRedisSaver
        except ImportError as err:
            raise ImportError(
                "使用 redis checkpointer 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[redis]"
            ) from err
        logger.info(f"创建 redis checkpointer, namespace={namespace}")
        async with AsyncRedisSaver.from_conn_string(config.url) as saver:
            ttl_minutes = config.ttl_minutes
            if ttl_minutes and ttl_minutes > 0:
                saver.ttl = {"default": ttl_minutes}
            yield saver

    else:
        raise ValueError(f"不支持的 checkpointer 类型: {checkpointer_type}")


@asynccontextmanager
async def create_store(
    namespace: str,
    *,
    agent_config: AgentConfig,
) -> AsyncGenerator[BaseStore, None]:
    """
    根据配置创建 LangGraph Store 实例

    使用 async with 确保资源正确关闭：
    ```python
    async with create_store(namespace, agent_config=config.agent) as store:
        graph = builder.compile(store=store)
        # 使用 graph...
    # 自动关闭连接
    ```

    Args:
        namespace: 命名空间（用于数据隔离）
        agent_config: AgentConfig

    Yields:
        LangGraph BaseStore 实例
    """
    config = agent_config.deliverable_store
    store_type = config.type

    if store_type == "memory":
        from langgraph.store.memory import InMemoryStore
        yield InMemoryStore()

    elif store_type == "postgres":
        if not config.url:
            raise ValueError("postgres store 需要配置 url")
        try:
            from langgraph.store.postgres.aio import AsyncPostgresStore
        except ImportError as err:
            raise ImportError(
                "使用 postgres store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[postgres]"
            ) from err
        logger.info(f"创建 postgres store, namespace={namespace}")
        async with AsyncPostgresStore.from_conn_string(config.url) as store:
            yield store

    elif store_type == "redis":
        if not config.url:
            raise ValueError("redis store 需要配置 url")
        try:
            from langgraph.store.redis.aio import AsyncRedisStore
        except ImportError as err:
            raise ImportError(
                "使用 redis store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[redis]"
            ) from err
        logger.info(f"创建 redis store, namespace={namespace}")
        async with AsyncRedisStore.from_conn_string(config.url) as store:
            yield store

    else:
        raise ValueError(f"不支持的 store 类型: {store_type}")


def _build_vector_store(
    namespace: str,
    *,
    vector_store_config: VectorStoreConfig,
    embedding_config: EmbeddingConfig,
) -> VectorStore:
    """
    根据配置创建 VectorStore 实例
    """
    store_type = vector_store_config.type

    if store_type == "lance":
        try:
            return LanceVectorStore(
                path=vector_store_config.path or "./data/vectors",
                namespace=namespace,
            )
        except ImportError as err:
            raise ImportError(
                "使用 lance vector_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[lance]"
            ) from err

    if store_type == "chroma":
        try:
            return ChromaVectorStore(
                path=vector_store_config.path or "./data/chroma",
                host=vector_store_config.host,
                port=vector_store_config.port,
                namespace=namespace,
            )
        except ImportError as err:
            raise ImportError(
                "使用 chroma vector_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[chroma]"
            ) from err

    if store_type == "milvus":
        if embedding_config.dimension is None:
            raise ValueError("Embedding dimension 未配置，无法创建 Milvus VectorStore")
        try:
            return MilvusVectorStore(
                uri=vector_store_config.uri or "./data/milvus.db",
                token=vector_store_config.token,
                namespace=namespace,
                dim=embedding_config.dimension,
            )
        except ImportError as err:
            raise ImportError(
                "使用 milvus vector_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[milvus]"
            ) from err

    raise ValueError(f"不支持的 vector_store 类型: {store_type}")


def create_learning_store(
    namespace: str,
    *,
    vector_store_config: VectorStoreConfig,
    embedding_config: EmbeddingConfig,
):
    """根据配置创建 ExperienceStore 实例（经验学习）"""
    if embedding_config.dimension is None:
        raise ValueError("Embedding dimension 未配置，无法创建 ExperienceStore")
    vector_store = _build_vector_store(
        VECTOR_DB_NAMESPACE,
        vector_store_config=vector_store_config,
        embedding_config=embedding_config,
    )
    return VectorExperienceStore(
        vector_store=vector_store,
        dimension=embedding_config.dimension,
        namespace=namespace,
    )


def create_knowledge_store(
    namespace: str,
    *,
    vector_store_config: VectorStoreConfig,
    embedding_config: EmbeddingConfig,
):
    """根据配置创建 KnowledgeStore 实例"""
    if embedding_config.dimension is None:
        raise ValueError("Embedding dimension 未配置，无法创建 KnowledgeStore")
    vector_store = _build_vector_store(
        VECTOR_DB_NAMESPACE,
        vector_store_config=vector_store_config,
        embedding_config=embedding_config,
    )
    return VectorKnowledgeStore(
        vector_store=vector_store,
        dimension=embedding_config.dimension,
        namespace=namespace,
    )


__all__ = [
    "create_checkpointer",
    "create_store",
    "create_learning_store",
    "create_knowledge_store",
]
