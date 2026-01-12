"""
DeliverableStore - 交付物存储

使用 LangGraph Store 存储 Agent 的交付物。

官方集成方式：
- compile(store=store) 传入 store 实例
- 节点内通过 get_store() 获取 store
- 用 store.aput() / store.aget() 操作

设计原则：
- 内容存 Redis Store（带 TTL，7天过期）
- 引用（ref）存在 Blackboard 里，由 Checkpointer 持久化

交付物引用格式：
- analysis:{uuid}  -> 需求分析结果
- plan:{uuid}      -> 执行计划
- workflow:{uuid}  -> 工作流
- review:{uuid}    -> Review 结果
"""

from __future__ import annotations

import logging
import time
import uuid
from typing import Any

from langgraph.store.redis.aio import AsyncRedisStore

from src.infrastructure.database.redis import RedisClient
from src.shared.config.settings import settings

logger = logging.getLogger(__name__)

# 元数据 key
_META_KEY = "_meta"


class DeliverableStore:
    """
    交付物存储（LangGraph Store 集成）

    提供 store 实例给 compile()，以及 namespace/ref 工具方法。
    支持 team_id 实现团队级别隔离。

    使用示例：
    ```python
    # 1. compile() 时传入 store
    store = await DeliverableStore.get_store_instance()
    app = graph.compile(checkpointer=saver, store=store)

    # 2. 节点内使用（带团队隔离）
    from langgraph.config import get_store

    async def my_node(state):
        store = get_store()
        team_id = "team_abc123"
        ns = DeliverableStore.namespace(session_id, team_id)

        # 存储
        ref = DeliverableStore.make_ref("analysis")
        await store.aput(ns, ref, {"content": data, "type": "analysis"})

        # 读取
        item = await store.aget(ns, ref)
        content = item.value["content"] if item else None
    ```
    """

    _store: AsyncRedisStore | None = None
    _initialized: bool = False

    @classmethod
    def _get_ttl_minutes(cls) -> float:
        """获取 TTL 配置（分钟）"""
        ttl_seconds = int(settings.get("redis_deliverable_ttl_seconds", 60 * 60 * 24 * 7))
        if ttl_seconds <= 0:
            return -1
        return ttl_seconds / 60.0

    @classmethod
    async def get_store_instance(cls) -> AsyncRedisStore:
        """
        获取 store 实例（给 compile() 用）

        Returns:
            AsyncRedisStore 实例
        """
        if cls._store is None or not cls._initialized:
            ttl_minutes = cls._get_ttl_minutes()
            ttl_config = {"default_ttl": ttl_minutes, "refresh_on_read": True}

            redis_client = await RedisClient.get_binary_client()

            logger.info(
                "初始化 DeliverableStore (复用连接池, ttl_minutes=%s)",
                ttl_minutes,
            )

            cls._store = AsyncRedisStore(redis_client=redis_client, ttl=ttl_config)
            await cls._store.setup()
            cls._initialized = True

        return cls._store

    # ==================== 工具方法 ====================

    @staticmethod
    def namespace(session_id: str, team_id: str = "default") -> tuple[str, str]:
        """生成 namespace（包含 team_id 实现隔离）"""
        return ("deliverable", f"{team_id}:{session_id}")

    @staticmethod
    def make_ref(dtype: str) -> str:
        """生成引用 ID"""
        return f"{dtype}:{uuid.uuid4().hex[:8]}"

    # ==================== 便捷方法（封装常用操作）====================

    @classmethod
    async def store(
        cls,
        store: Any,
        session_id: str,
        dtype: str,
        content: Any,
        team_id: str = "default",
    ) -> str:
        """
        存储交付物（便捷方法）

        Args:
            store: LangGraph store（从 get_store() 获取）
            session_id: 会话 ID
            dtype: 类型（analysis/workflow/review 等）
            content: 内容
            team_id: 团队 ID（用于隔离）

        Returns:
            引用 ID
        """
        ns = cls.namespace(session_id, team_id)
        ref = cls.make_ref(dtype)

        await store.aput(
            ns,
            ref,
            {
                "content": content,
                "type": dtype,
                "created_at_ms": int(time.time() * 1000),
            },
        )

        # 更新 latest ref
        await cls._update_latest_ref(store, session_id, dtype, ref, team_id)

        logger.debug("存储交付物: team=%s, session=%s, ref=%s", team_id, session_id, ref)
        return ref

    @classmethod
    async def _update_latest_ref(
        cls,
        store: Any,
        session_id: str,
        dtype: str,
        ref: str,
        team_id: str = "default",
    ) -> None:
        """更新 latest ref 元数据"""
        ns = cls.namespace(session_id, team_id)

        meta_item = await store.aget(ns, _META_KEY)
        if meta_item:
            meta = meta_item.value
        else:
            meta = {}

        meta[f"latest_{dtype}_ref"] = ref
        meta["updated_at_ms"] = int(time.time() * 1000)

        await store.aput(ns, _META_KEY, meta)

    @classmethod
    async def get(
        cls,
        store: Any,
        session_id: str,
        ref: str,
        team_id: str = "default",
    ) -> Any | None:
        """
        获取交付物（通过引用）

        Args:
            store: LangGraph store
            session_id: 会话 ID
            ref: 引用 ID
            team_id: 团队 ID（用于隔离）

        Returns:
            内容，或 None
        """
        ns = cls.namespace(session_id, team_id)
        item = await store.aget(ns, ref)
        if item and item.value:
            return item.value.get("content")
        return None

    @classmethod
    async def get_latest(
        cls,
        store: Any,
        session_id: str,
        dtype: str,
        team_id: str = "default",
    ) -> Any | None:
        """
        获取最新的某类型交付物

        Args:
            store: LangGraph store
            session_id: 会话 ID
            dtype: 类型
            team_id: 团队 ID（用于隔离）

        Returns:
            内容，或 None
        """
        ns = cls.namespace(session_id, team_id)

        meta_item = await store.aget(ns, _META_KEY)
        if not meta_item or not meta_item.value:
            return None

        ref = meta_item.value.get(f"latest_{dtype}_ref")
        if not ref:
            return None

        return await cls.get(store, session_id, ref, team_id)

    @classmethod
    async def get_latest_ref(
        cls,
        store: Any,
        session_id: str,
        dtype: str,
        team_id: str = "default",
    ) -> str | None:
        """获取最新的某类型交付物的引用"""
        ns = cls.namespace(session_id, team_id)

        meta_item = await store.aget(ns, _META_KEY)
        if not meta_item or not meta_item.value:
            return None

        return meta_item.value.get(f"latest_{dtype}_ref")

    # ==================== 清理操作 ====================

    @classmethod
    async def clear(cls, store: Any, session_id: str, team_id: str = "default") -> None:
        """清理会话的所有交付物"""
        ns = cls.namespace(session_id, team_id)

        async for item in store.asearch(ns):
            await store.adelete(ns, item.key)

        logger.info("清理会话交付物: team=%s, session=%s", team_id, session_id)

    @classmethod
    async def close(cls) -> None:
        """关闭 store（应用退出时调用）"""
        cls._store = None
        cls._initialized = False
        logger.info("DeliverableStore 已关闭")
