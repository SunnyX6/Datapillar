# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
Storage module - unified storage management.

Checkpoint/deliverable storage is isolated by namespace. Vector storage uses a
fixed database name "datapillar"; when no database exists, isolation is handled
by directory/collection prefixes and the namespace field.

Async context manager factories:
1. create_checkpointer(namespace, agent_config=...) - create LangGraph Checkpointer
2. create_store(namespace, agent_config=...) - create LangGraph Store
3. create_learning_store(namespace, vector_store_config=..., embedding_config=...) - create experience store
4. create_knowledge_store(namespace, vector_store_config=..., embedding_config=...) - create knowledge store

Example:
```python
from datapillar_oneagentic.storage import (
    create_checkpointer,
    create_store,
    create_learning_store,
    create_knowledge_store,
)

# Use async with to ensure resources are closed
namespace = "sales_app"

async with create_checkpointer(namespace, agent_config=config.agent) as checkpointer:
    async with create_store(namespace, agent_config=config.agent) as store:
        graph = builder.compile(checkpointer=checkpointer, store=store)
        # Use graph...
# Exiting the context closes connections
```

Config example (config.toml):
```toml
[agent.checkpointer]
type = "memory"  # memory | sqlite | postgres | redis
path = "./data/checkpoints"  # sqlite only
url = "redis://localhost:6379"  # redis/postgres only

[agent.deliverable_store]
type = "memory"  # memory | postgres | redis
url = "redis://localhost:6379"  # redis/postgres only

[learning.vector_store]
type = "lance"  # lance | chroma | milvus
path = "./data/vectors"

```
"""

from __future__ import annotations

import logging
import os
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager
from typing import TYPE_CHECKING, Any

from datapillar_oneagentic.core.config import AgentConfig
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig
from datapillar_oneagentic.storage.vector_stores.base import VectorStore

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
    Create a LangGraph Checkpointer from configuration.

    Use async with to ensure resources close properly:
    ```python
    async with create_checkpointer(namespace, agent_config=config.agent) as checkpointer:
        graph = builder.compile(checkpointer=checkpointer)
        # Use graph...
    # Connections are closed automatically
    ```

    Args:
        namespace: Namespace for data isolation
        agent_config: AgentConfig

    Yields:
        LangGraph BaseCheckpointSaver instance
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
                "SQLite checkpointer requires extra dependencies:\n"
                "  pip install datapillar-oneagentic[sqlite]"
            ) from err
        logger.info(f"Created sqlite checkpointer: {db_path}")
        async with AsyncSqliteSaver.from_conn_string(db_path) as saver:
            yield saver

    elif checkpointer_type == "postgres":
        if not config.url:
            raise ValueError("postgres checkpointer requires url")
        try:
            from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        except ImportError as err:
            raise ImportError(
                "Postgres checkpointer requires extra dependencies:\n"
                "  pip install datapillar-oneagentic[postgres]"
            ) from err
        logger.info(f"Created postgres checkpointer, namespace={namespace}")
        async with AsyncPostgresSaver.from_conn_string(config.url) as saver:
            yield saver

    elif checkpointer_type == "redis_shallow":
        if not config.url:
            raise ValueError("redis_shallow checkpointer requires url")
        try:
            from langgraph.checkpoint.redis.ashallow import AsyncShallowRedisSaver
        except ImportError as err:
            raise ImportError(
                "redis_shallow checkpointer requires extra dependencies:\n"
                "  pip install datapillar-oneagentic[redis]"
            ) from err
        logger.info(f"Created redis_shallow checkpointer, namespace={namespace}")
        async with AsyncShallowRedisSaver.from_conn_string(config.url) as saver:
            ttl_minutes = config.ttl_minutes
            if ttl_minutes and ttl_minutes > 0:
                saver.ttl = {"default": ttl_minutes}
            yield saver

    elif checkpointer_type == "redis":
        if not config.url:
            raise ValueError("redis checkpointer requires url")
        try:
            from langgraph.checkpoint.redis.aio import AsyncRedisSaver
        except ImportError as err:
            raise ImportError(
                "Redis checkpointer requires extra dependencies:\n"
                "  pip install datapillar-oneagentic[redis]"
            ) from err
        logger.info(f"Created redis checkpointer, namespace={namespace}")
        async with AsyncRedisSaver.from_conn_string(config.url) as saver:
            ttl_minutes = config.ttl_minutes
            if ttl_minutes and ttl_minutes > 0:
                saver.ttl = {"default": ttl_minutes}
            yield saver

    else:
        raise ValueError(f"Unsupported checkpointer type: {checkpointer_type}")


@asynccontextmanager
async def create_store(
    namespace: str,
    *,
    agent_config: AgentConfig,
) -> AsyncGenerator[BaseStore, None]:
    """
    Create a LangGraph Store from configuration.

    Use async with to ensure resources close properly:
    ```python
    async with create_store(namespace, agent_config=config.agent) as store:
        graph = builder.compile(store=store)
        # Use graph...
    # Connections are closed automatically
    ```

    Args:
        namespace: Namespace for data isolation
        agent_config: AgentConfig

    Yields:
        LangGraph BaseStore instance
    """
    config = agent_config.deliverable_store
    store_type = config.type

    if store_type == "memory":
        from langgraph.store.memory import InMemoryStore
        yield InMemoryStore()

    elif store_type == "postgres":
        if not config.url:
            raise ValueError("postgres store requires url")
        try:
            from langgraph.store.postgres.aio import AsyncPostgresStore
        except ImportError as err:
            raise ImportError(
                "Postgres store requires extra dependencies:\n"
                "  pip install datapillar-oneagentic[postgres]"
            ) from err
        logger.info(f"Created postgres store, namespace={namespace}")
        async with AsyncPostgresStore.from_conn_string(config.url) as store:
            yield store

    elif store_type == "redis":
        if not config.url:
            raise ValueError("redis store requires url")
        try:
            from langgraph.store.redis.aio import AsyncRedisStore
        except ImportError as err:
            raise ImportError(
                "Redis store requires extra dependencies:\n"
                "  pip install datapillar-oneagentic[redis]"
            ) from err
        logger.info(f"Created redis store, namespace={namespace}")
        async with AsyncRedisStore.from_conn_string(config.url) as store:
            yield store

    else:
        raise ValueError(f"Unsupported store type: {store_type}")


def _build_vector_store(
    namespace: str,
    *,
    vector_store_config: VectorStoreConfig,
    embedding_config: EmbeddingConfig,
) -> VectorStore:
    """
    Create a VectorStore from configuration.
    """
    from datapillar_oneagentic.storage.vector_stores import (
        ChromaVectorStore,
        LanceVectorStore,
        MilvusVectorStore,
    )

    store_type = vector_store_config.type

    if store_type == "lance":
        try:
            return LanceVectorStore(
                path=vector_store_config.path or "./data/vectors",
                namespace=namespace,
            )
        except ImportError as err:
            raise ImportError(
                "Lance vector_store requires extra dependencies:\n"
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
                "Chroma vector_store requires extra dependencies:\n"
                "  pip install datapillar-oneagentic[chroma]"
            ) from err

    if store_type == "milvus":
        if embedding_config.dimension is None:
            raise ValueError("Embedding dimension is not configured for Milvus VectorStore")
        try:
            backend_params = _resolve_backend_params(vector_store_config)
            return MilvusVectorStore(
                uri=vector_store_config.uri or "./data/milvus.db",
                token=vector_store_config.token,
                user=vector_store_config.user,
                password=vector_store_config.password,
                db_name=vector_store_config.db_name,
                namespace=namespace,
                dim=embedding_config.dimension,
                index_params=vector_store_config.index_params,
                sparse_index_params=vector_store_config.sparse_index_params,
                search_params=vector_store_config.search_params,
                sparse_search_params=vector_store_config.sparse_search_params,
                params=backend_params,
            )
        except ImportError as err:
            raise ImportError(
                "Milvus vector_store requires extra dependencies:\n"
                "  pip install datapillar-oneagentic[milvus]"
            ) from err

    raise ValueError(f"Unsupported vector_store type: {store_type}")


def create_learning_store(
    namespace: str,
    *,
    vector_store_config: VectorStoreConfig,
    embedding_config: EmbeddingConfig,
):
    """Create an ExperienceStore from configuration."""
    from datapillar_oneagentic.storage.learning_stores.vector import VectorExperienceStore

    if embedding_config.dimension is None:
        raise ValueError("Embedding dimension is not configured for ExperienceStore")
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
    """Create a KnowledgeStore from configuration."""
    from datapillar_oneagentic.storage.knowledge_stores.vector import VectorKnowledgeStore

    if embedding_config.dimension is None:
        raise ValueError("Embedding dimension is not configured for KnowledgeStore")
    driver = _resolve_vector_store_driver(vector_store_config)
    if driver:
        raise ValueError("VectorStoreConfig.params.driver is internal and must not be set.")
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


def _resolve_vector_store_driver(config: VectorStoreConfig) -> str | None:
    params = dict(config.params or {})
    extra = getattr(config, "model_extra", None) or {}
    driver = params.get("driver") or extra.get("driver")
    return str(driver).lower() if isinstance(driver, str) and driver else None


def _resolve_backend_params(config: VectorStoreConfig) -> dict[str, Any]:
    params = dict(config.params or {})
    extra = getattr(config, "model_extra", None) or {}
    if "bm25" in extra and "bm25" not in params:
        params["bm25"] = extra.get("bm25")
    return params


__all__ = [
    "create_checkpointer",
    "create_store",
    "create_learning_store",
    "create_knowledge_store",
]
