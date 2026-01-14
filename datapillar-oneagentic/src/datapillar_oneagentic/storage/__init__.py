"""
Storage 模块 - 统一存储管理

所有存储都按 namespace 隔离，namespace 是最高层级的数据隔离边界。

提供上下文管理器工厂函数：
1. create_checkpointer(namespace) - 创建 LangGraph Checkpointer
2. create_store(namespace) - 创建 LangGraph Store
3. create_learning_store(namespace) - 创建经验向量库

使用示例：
```python
from datapillar_oneagentic.storage import (
    create_checkpointer,
    create_store,
    create_learning_store,
)

# 使用 async with 确保资源正确关闭
namespace = "sales_app"

async with create_checkpointer(namespace) as checkpointer:
    async with create_store(namespace) as store:
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

[agent.learning_store]
type = "lance"  # lance | chroma | milvus
path = "./data/experience"
```
"""

from __future__ import annotations

import logging
import os
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from langgraph.checkpoint.base import BaseCheckpointSaver
    from langgraph.store.base import BaseStore

logger = logging.getLogger(__name__)


@asynccontextmanager
async def create_checkpointer(namespace: str) -> AsyncGenerator[BaseCheckpointSaver, None]:
    """
    根据配置创建 LangGraph Checkpointer 实例

    使用 async with 确保资源正确关闭：
    ```python
    async with create_checkpointer(namespace) as checkpointer:
        graph = builder.compile(checkpointer=checkpointer)
        # 使用 graph...
    # 自动关闭连接
    ```

    Args:
        namespace: 命名空间（用于数据隔离）

    Yields:
        LangGraph BaseCheckpointSaver 实例
    """
    from datapillar_oneagentic.config import get_config

    config = get_config().agent.checkpointer
    checkpointer_type = config.type

    if checkpointer_type == "memory":
        from langgraph.checkpoint.memory import MemorySaver
        yield MemorySaver()

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
            if config.ttl_minutes and config.ttl_minutes > 0:
                saver.ttl = {"default": config.ttl_minutes}
            yield saver

    else:
        raise ValueError(f"不支持的 checkpointer 类型: {checkpointer_type}")


@asynccontextmanager
async def create_store(namespace: str) -> AsyncGenerator[BaseStore, None]:
    """
    根据配置创建 LangGraph Store 实例

    使用 async with 确保资源正确关闭：
    ```python
    async with create_store(namespace) as store:
        graph = builder.compile(store=store)
        # 使用 graph...
    # 自动关闭连接
    ```

    Args:
        namespace: 命名空间（用于数据隔离）

    Yields:
        LangGraph BaseStore 实例
    """
    from datapillar_oneagentic.config import get_config

    config = get_config().agent.deliverable_store
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


def create_learning_store(namespace: str):
    """
    根据配置创建 ExperienceStore 实例（经验学习）

    Args:
        namespace: 命名空间（用于数据隔离）

    Returns:
        ExperienceStore 实例
    """
    from datapillar_oneagentic.config import get_config

    config = get_config().agent.learning_store
    store_type = config.type

    if store_type == "lance":
        try:
            from datapillar_oneagentic.storage.learning_stores.lance import LanceExperienceStore
        except ImportError as err:
            raise ImportError(
                "使用 lance learning_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[lance]"
            ) from err
        path = config.path or "./data/experience"
        return LanceExperienceStore(path=path, namespace=namespace)

    elif store_type == "chroma":
        try:
            from datapillar_oneagentic.storage.learning_stores.chroma import ChromaExperienceStore
        except ImportError as err:
            raise ImportError(
                "使用 chroma learning_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[chroma]"
            ) from err
        path = config.path or "./data/chroma"
        host = getattr(config, "host", None)
        port = getattr(config, "port", 8000)
        return ChromaExperienceStore(path=path, host=host, port=port, namespace=namespace)

    elif store_type == "milvus":
        try:
            from datapillar_oneagentic.storage.learning_stores.milvus import MilvusExperienceStore
        except ImportError as err:
            raise ImportError(
                "使用 milvus learning_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[milvus]"
            ) from err
        uri = config.uri or "./data/milvus.db"
        token = getattr(config, "token", None)
        return MilvusExperienceStore(uri=uri, token=token, namespace=namespace)

    else:
        raise ValueError(f"不支持的 learning_store 类型: {store_type}")


__all__ = [
    "create_checkpointer",
    "create_store",
    "create_learning_store",
]
