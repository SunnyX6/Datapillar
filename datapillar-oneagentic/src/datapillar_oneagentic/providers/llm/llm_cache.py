"""
LLM 精确缓存

设计原则：
- 精确匹配：相同内容 = 命中，任何差异 = 不命中
- 规范化处理：去除 LangChain 消息中的动态 ID，只保留实际内容
- 可选 Redis 存储：支持 TTL，分布式友好
- 简单可靠：无 embedding、无相似度计算、无误判
- 同步接口：LangChain BaseCache 要求同步
"""

from __future__ import annotations

import hashlib
import json
import logging
import threading
from typing import Any

from langchain_core.caches import RETURN_VAL_TYPE, BaseCache
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, Generation

logger = logging.getLogger(__name__)


def _normalize_prompt(prompt: str) -> str:
    """
    规范化 prompt 用于缓存 key 计算

    解决 LangChain 的已知问题：消息序列化时包含动态生成的唯一 ID，
    导致相同内容的消息 hash 不同，缓存无法命中。

    处理逻辑：
    - 解析 JSON 格式的消息列表
    - 只保留 type/role 和 content，去除 id 等动态字段
    - 返回规范化后的 JSON 字符串
    """
    try:
        raw = json.loads(prompt)
        if not isinstance(raw, list):
            return prompt

        normalized: list[dict[str, Any]] = []
        for item in raw:
            if not isinstance(item, dict):
                continue

            # 提取 type（兼容多种格式）
            msg_type = (
                item.get("type")
                or item.get("kwargs", {}).get("type")
                or item.get("role")
                or "unknown"
            )

            # 提取 content（兼容多种格式）
            content = item.get("content") or item.get("kwargs", {}).get("content")

            # content 可能是字符串或 content blocks 列表
            if isinstance(content, list):
                text_parts = []
                for block in content:
                    if isinstance(block, str):
                        text_parts.append(block)
                    elif isinstance(block, dict):
                        text = block.get("text")
                        if text:
                            text_parts.append(str(text))
                content = "\n".join(text_parts)
            elif content is None:
                content = ""
            else:
                content = str(content)

            normalized.append({"type": str(msg_type), "content": content})

        return json.dumps(normalized, ensure_ascii=False, sort_keys=True)

    except (json.JSONDecodeError, TypeError):
        return prompt


def _compute_cache_key(prompt: str, llm_string: str) -> str:
    """计算缓存 key"""
    normalized = _normalize_prompt(prompt)
    raw_key = f"{normalized}:{llm_string}"
    return hashlib.sha256(raw_key.encode("utf-8")).hexdigest()


def _serialize_return_val(return_val: RETURN_VAL_TYPE) -> str:
    """序列化 LLM 返回值"""
    items: list[dict[str, Any]] = []
    for gen in return_val or []:
        msg = getattr(gen, "message", None)
        if msg is not None:
            content = getattr(msg, "content", None)
            try:
                json.dumps(content)
                serializable_content = content
            except TypeError:
                serializable_content = str(content) if content else ""
            items.append({"type": "chat", "content": serializable_content})
        else:
            text = getattr(gen, "text", None)
            items.append({"type": "text", "text": str(text or "")})
    return json.dumps(items, ensure_ascii=False)


def _deserialize_return_val(data: str | bytes) -> RETURN_VAL_TYPE | None:
    """反序列化 LLM 返回值"""
    try:
        if isinstance(data, bytes):
            data = data.decode("utf-8")
        raw = json.loads(data)
        if not isinstance(raw, list):
            return None

        result: list[Any] = []
        for item in raw:
            if not isinstance(item, dict):
                continue
            item_type = item.get("type")
            if item_type == "chat":
                content = item.get("content", "")
                result.append(ChatGeneration(message=AIMessage(content=content)))
            elif item_type == "text":
                result.append(Generation(text=str(item.get("text", ""))))
        return result if result else None

    except (json.JSONDecodeError, TypeError, UnicodeDecodeError):
        return None


class InMemoryLLMCache(BaseCache):
    """
    内存 LLM 缓存（默认实现）

    特点：
    - 精确匹配：相同内容才命中
    - 规范化处理：去除动态 ID
    - 内存存储：简单可靠
    - 支持 TTL（通过定期清理）
    - 线程安全
    """

    def __init__(
        self,
        *,
        ttl_seconds: int = 300,
        max_size: int = 1000,
    ):
        self.ttl_seconds = ttl_seconds
        self.max_size = max_size
        self._cache: dict[str, tuple[str, float]] = {}  # key -> (value, timestamp)
        self._lock = threading.RLock()  # 可重入锁，保证线程安全

    def lookup(self, prompt: str, llm_string: str) -> RETURN_VAL_TYPE | None:
        """查询缓存"""
        import time

        cache_key = _compute_cache_key(prompt, llm_string)

        with self._lock:
            if cache_key not in self._cache:
                return None

            data, timestamp = self._cache[cache_key]

            # 检查 TTL
            if time.time() - timestamp > self.ttl_seconds:
                del self._cache[cache_key]
                return None

        result = _deserialize_return_val(data)
        return result

    def update(self, prompt: str, llm_string: str, return_val: RETURN_VAL_TYPE) -> None:
        """更新缓存"""
        import time

        if not return_val:
            return

        # 检查是否有实际内容
        has_content = False
        for gen in return_val:
            msg = getattr(gen, "message", None)
            if msg and getattr(msg, "content", None):
                has_content = True
                break
            if getattr(gen, "text", None):
                has_content = True
                break

        if not has_content:
            return

        cache_key = _compute_cache_key(prompt, llm_string)
        data = _serialize_return_val(return_val)

        with self._lock:
            # 清理过期和超限
            self._cleanup()
            self._cache[cache_key] = (data, time.time())


    def _cleanup(self) -> None:
        """清理过期和超限的缓存（调用方需持有锁）"""
        import time

        current_time = time.time()

        # 清理过期：先复制 keys 避免迭代时修改
        expired_keys = [
            k for k, (_, ts) in list(self._cache.items())
            if current_time - ts > self.ttl_seconds
        ]
        for k in expired_keys:
            del self._cache[k]

        # 清理超限（LRU 简化版：按时间戳排序删除最旧的）
        if len(self._cache) > self.max_size:
            sorted_keys = sorted(
                self._cache.keys(),
                key=lambda k: self._cache[k][1]
            )
            to_remove = len(self._cache) - self.max_size
            for k in sorted_keys[:to_remove]:
                del self._cache[k]

    def clear(self, **kwargs: Any) -> None:
        """清空缓存"""
        with self._lock:
            self._cache.clear()
        logger.info("LLM 缓存已清空")


class RedisLLMCache(BaseCache):
    """
    Redis LLM 缓存

    需要安装 redis 包：pip install datapillar-oneagentic[redis]
    """

    def __init__(
        self,
        *,
        redis_url: str = "redis://localhost:6379",
        ttl_seconds: int = 300,
        key_prefix: str = "llm_cache:",
    ):
        self.ttl_seconds = ttl_seconds
        self.key_prefix = key_prefix
        self._redis_url = redis_url
        self._redis = None
        logger.info(f"LLM Redis 缓存初始化: ttl={ttl_seconds}s, prefix={key_prefix}")

    def _get_redis(self):
        """获取 Redis 客户端（延迟初始化）"""
        if self._redis is None:
            import redis
            self._redis = redis.from_url(self._redis_url, decode_responses=True)
        return self._redis

    def lookup(self, prompt: str, llm_string: str) -> RETURN_VAL_TYPE | None:
        """查询缓存"""
        try:
            client = self._get_redis()
            cache_key = self.key_prefix + _compute_cache_key(prompt, llm_string)

            data = client.get(cache_key)
            if data is None:
                return None

            result = _deserialize_return_val(data)
            return result

        except Exception as e:
            logger.warning(f"Redis 缓存查询失败: {e}")
            return None

    def update(self, prompt: str, llm_string: str, return_val: RETURN_VAL_TYPE) -> None:
        """更新缓存"""
        if not return_val:
            return

        # 检查是否有实际内容
        has_content = False
        for gen in return_val:
            msg = getattr(gen, "message", None)
            if msg and getattr(msg, "content", None):
                has_content = True
                break
            if getattr(gen, "text", None):
                has_content = True
                break

        if not has_content:
            return

        try:
            client = self._get_redis()
            cache_key = self.key_prefix + _compute_cache_key(prompt, llm_string)
            data = _serialize_return_val(return_val)

            client.set(cache_key, data, ex=self.ttl_seconds)

        except Exception as e:
            logger.warning(f"Redis 缓存写入失败: {e}")

    def clear(self, **kwargs: Any) -> None:
        """清空缓存"""
        try:
            client = self._get_redis()
            cursor = 0
            deleted = 0
            while True:
                cursor, keys = client.scan(cursor, match=f"{self.key_prefix}*", count=100)
                if keys:
                    client.delete(*keys)
                    deleted += len(keys)
                if cursor == 0:
                    break
            logger.info(f"LLM Redis 缓存已清空: 删除 {deleted} 条记录")
        except Exception as e:
            logger.warning(f"Redis 缓存清空失败: {e}")


def create_llm_cache() -> BaseCache | None:
    """
    创建 LLM 缓存实例（从配置读取）

    配置项（在 llm.cache 下）：
    - enabled: 是否启用缓存（默认 True）
    - backend: 缓存后端 memory 或 redis（默认 memory）
    - ttl_seconds: TTL 秒（默认 300）
    - max_size: 内存缓存最大条目数（默认 1000）
    - redis_url: Redis URL（backend=redis 时必填）
    - key_prefix: Redis key 前缀（默认 llm_cache:）
    """
    from datapillar_oneagentic.config import datapillar

    cache_config = datapillar.llm.cache

    if not cache_config.enabled:
        return None

    backend = cache_config.backend.lower()

    if backend == "redis":
        if not cache_config.redis_url:
            logger.warning("LLM 缓存配置 backend=redis，但未设置 redis_url，降级为内存缓存")
            return InMemoryLLMCache(
                ttl_seconds=cache_config.ttl_seconds,
                max_size=cache_config.max_size,
            )

        return RedisLLMCache(
            redis_url=cache_config.redis_url,
            ttl_seconds=cache_config.ttl_seconds,
            key_prefix=cache_config.key_prefix,
        )
    else:
        return InMemoryLLMCache(
            ttl_seconds=cache_config.ttl_seconds,
            max_size=cache_config.max_size,
        )
