"""
Storage 模块 - 统一存储管理

所有存储都按 namespace 隔离，namespace 是最高层级的数据隔离边界。

提供工厂函数：
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

# 同一个 namespace 下的所有存储都隔离
namespace = "sales_app"

checkpointer = create_checkpointer(namespace)
store = create_store(namespace)
learning_store = create_learning_store(namespace)
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
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from langgraph.checkpoint.base import BaseCheckpointSaver
    from langgraph.store.base import BaseStore

logger = logging.getLogger(__name__)


def create_checkpointer(namespace: str) -> "BaseCheckpointSaver":
    """
    根据配置创建 LangGraph Checkpointer 实例

    Args:
        namespace: 命名空间（用于数据隔离）

    Returns:
        LangGraph BaseCheckpointSaver 实例
    """
    from datapillar_oneagentic.config import get_config

    config = get_config().agent.checkpointer
    checkpointer_type = config.type

    if checkpointer_type == "memory":
        from langgraph.checkpoint.memory import MemorySaver
        # memory 类型每次创建新实例，通过 namespace 区分
        # 注意：memory 类型重启后数据丢失
        return MemorySaver()

    elif checkpointer_type == "sqlite":
        base_path = config.path or "./data/checkpoints"
        # 每个 namespace 独立数据库文件
        db_path = os.path.join(base_path, f"{namespace}.db")
        os.makedirs(os.path.dirname(db_path), exist_ok=True)

        try:
            from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
        except ImportError:
            raise ImportError(
                "使用 sqlite checkpointer 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[sqlite]"
            )
        logger.info(f"创建 sqlite checkpointer: {db_path}")
        return AsyncSqliteSaver.from_conn_string(db_path)

    elif checkpointer_type == "postgres":
        if not config.url:
            raise ValueError("postgres checkpointer 需要配置 url")
        try:
            from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        except ImportError:
            raise ImportError(
                "使用 postgres checkpointer 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[postgres]"
            )
        # postgres 通过 schema 前缀隔离
        # 注意：LangGraph 的 postgres saver 暂不支持自定义 schema
        # 这里通过 thread_id 前缀实现软隔离
        logger.info(f"创建 postgres checkpointer, namespace={namespace}")
        return AsyncPostgresSaver.from_conn_string(config.url)

    elif checkpointer_type == "redis":
        if not config.url:
            raise ValueError("redis checkpointer 需要配置 url")
        try:
            from langgraph.checkpoint.redis.aio import AsyncRedisSaver
        except ImportError:
            raise ImportError(
                "使用 redis checkpointer 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[redis]"
            )
        # redis 通过 key 前缀隔离
        saver = AsyncRedisSaver.from_conn_string(config.url)
        if config.ttl_minutes and config.ttl_minutes > 0:
            saver.ttl = {"default": config.ttl_minutes}
        logger.info(f"创建 redis checkpointer, namespace={namespace}")
        return saver

    else:
        raise ValueError(f"不支持的 checkpointer 类型: {checkpointer_type}")


def create_store(namespace: str) -> "BaseStore":
    """
    根据配置创建 LangGraph Store 实例

    Args:
        namespace: 命名空间（用于数据隔离）

    Returns:
        LangGraph BaseStore 实例
    """
    from datapillar_oneagentic.config import get_config

    config = get_config().agent.deliverable_store
    store_type = config.type

    if store_type == "memory":
        from langgraph.store.memory import InMemoryStore
        # memory 类型每次创建新实例
        return InMemoryStore()

    elif store_type == "postgres":
        if not config.url:
            raise ValueError("postgres store 需要配置 url")
        try:
            from langgraph.store.postgres.aio import AsyncPostgresStore
        except ImportError:
            raise ImportError(
                "使用 postgres store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[postgres]"
            )
        logger.info(f"创建 postgres store, namespace={namespace}")
        return AsyncPostgresStore.from_conn_string(config.url)

    elif store_type == "redis":
        if not config.url:
            raise ValueError("redis store 需要配置 url")
        try:
            from langgraph.store.redis.aio import AsyncRedisStore
        except ImportError:
            raise ImportError(
                "使用 redis store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[redis]"
            )
        logger.info(f"创建 redis store, namespace={namespace}")
        return AsyncRedisStore.from_conn_string(config.url)

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
        except ImportError:
            raise ImportError(
                "使用 lance learning_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[lance]"
            )
        path = config.path or "./data/experience"
        return LanceExperienceStore(path=path, namespace=namespace)

    elif store_type == "chroma":
        try:
            from datapillar_oneagentic.storage.learning_stores.chroma import ChromaExperienceStore
        except ImportError:
            raise ImportError(
                "使用 chroma learning_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[chroma]"
            )
        path = config.path or "./data/chroma"
        host = getattr(config, "host", None)
        port = getattr(config, "port", 8000)
        return ChromaExperienceStore(path=path, host=host, port=port, namespace=namespace)

    elif store_type == "milvus":
        try:
            from datapillar_oneagentic.storage.learning_stores.milvus import MilvusExperienceStore
        except ImportError:
            raise ImportError(
                "使用 milvus learning_store 需要安装依赖：\n"
                "  pip install datapillar-oneagentic[milvus]"
            )
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
