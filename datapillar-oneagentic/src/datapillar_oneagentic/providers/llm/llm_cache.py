# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27
"""
LLM exact cache.

Design principles:
- Exact match: identical content = hit, any difference = miss
- Normalization: remove dynamic IDs from LangChain messages
- Optional Redis storage: TTL support, friendly to distributed setups
- Simple and reliable: no embeddings, no similarity heuristics
- Sync interface: LangChain BaseCache is synchronous
"""

from __future__ import annotations

import hashlib
import json
import logging
import threading
from typing import Any

from langchain_core.caches import RETURN_VAL_TYPE, BaseCache
from langchain_core.outputs import ChatGeneration, Generation
from datapillar_oneagentic.providers.llm.config import LLMCacheConfig
from datapillar_oneagentic.messages.adapters.langchain import build_ai_message

logger = logging.getLogger(__name__)


def _normalize_prompt(prompt: str) -> str:
    """
    Normalize prompt for cache key calculation.

    Fixes a LangChain issue where serialized messages contain dynamic IDs,
    causing identical content to hash differently.

    Logic:
    - Parse JSON list of messages
    - Keep only type/role and content, drop dynamic fields like id
    - Return normalized JSON string
    """
    try:
        raw = json.loads(prompt)
        if not isinstance(raw, list):
            return prompt

        normalized: list[dict[str, Any]] = []
        for item in raw:
            if not isinstance(item, dict):
                continue

            # Extract type (support multiple formats).
            msg_type = (
                item.get("type")
                or item.get("kwargs", {}).get("type")
                or item.get("role")
                or "unknown"
            )

            # Extract content (support multiple formats).
            content = item.get("content") or item.get("kwargs", {}).get("content")

            # Content may be a string or a list of content blocks.
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
    """Compute cache key."""
    normalized = _normalize_prompt(prompt)
    raw_key = f"{normalized}:{llm_string}"
    return hashlib.sha256(raw_key.encode("utf-8")).hexdigest()


def _serialize_return_val(return_val: RETURN_VAL_TYPE) -> str:
    """Serialize LLM return value."""
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
    """Deserialize LLM return value."""
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
                result.append(ChatGeneration(message=build_ai_message(content)))
            elif item_type == "text":
                result.append(Generation(text=str(item.get("text", ""))))
        return result if result else None

    except (json.JSONDecodeError, TypeError, UnicodeDecodeError):
        return None


class InMemoryLLMCache(BaseCache):
    """
    In-memory LLM cache (default).

    Characteristics:
    - Exact match
    - Normalized prompts (drop dynamic IDs)
    - In-memory storage
    - TTL via periodic cleanup
    - Thread-safe
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
        self._lock = threading.RLock()  # Re-entrant lock for thread safety.

    def lookup(self, prompt: str, llm_string: str) -> RETURN_VAL_TYPE | None:
        """Lookup cache."""
        import time

        cache_key = _compute_cache_key(prompt, llm_string)

        with self._lock:
            if cache_key not in self._cache:
                return None

            data, timestamp = self._cache[cache_key]

            # Check TTL.
            if time.time() - timestamp > self.ttl_seconds:
                del self._cache[cache_key]
                return None

        result = _deserialize_return_val(data)
        return result

    def update(self, prompt: str, llm_string: str, return_val: RETURN_VAL_TYPE) -> None:
        """Update cache."""
        import time

        if not return_val:
            return

        # Ensure there is content.
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
            # Cleanup expired and oversized entries.
            self._cleanup()
            self._cache[cache_key] = (data, time.time())


    def _cleanup(self) -> None:
        """Cleanup expired and oversized cache entries (lock required)."""
        import time

        current_time = time.time()

        # Expired entries: copy keys to avoid mutation during iteration.
        expired_keys = [
            k for k, (_, ts) in list(self._cache.items())
            if current_time - ts > self.ttl_seconds
        ]
        for k in expired_keys:
            del self._cache[k]

        # Oversize cleanup (simplified LRU: remove oldest by timestamp).
        if len(self._cache) > self.max_size:
            sorted_keys = sorted(
                self._cache.keys(),
                key=lambda k: self._cache[k][1]
            )
            to_remove = len(self._cache) - self.max_size
            for k in sorted_keys[:to_remove]:
                del self._cache[k]

    def clear(self, **kwargs: Any) -> None:
        """Clear cache."""
        with self._lock:
            self._cache.clear()
        logger.info("LLM cache cleared")


class RedisLLMCache(BaseCache):
    """
    Redis LLM cache.

    Requires redis package: pip install datapillar-oneagentic[redis]
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
        logger.info(f"LLM Redis cache initialized: ttl={ttl_seconds}s, prefix={key_prefix}")

    def _get_redis(self):
        """Get Redis client (lazy init)."""
        if self._redis is None:
            import redis
            self._redis = redis.from_url(self._redis_url, decode_responses=True)
        return self._redis

    def lookup(self, prompt: str, llm_string: str) -> RETURN_VAL_TYPE | None:
        """Lookup cache."""
        try:
            client = self._get_redis()
            cache_key = self.key_prefix + _compute_cache_key(prompt, llm_string)

            data = client.get(cache_key)
            if data is None:
                return None

            result = _deserialize_return_val(data)
            return result

        except Exception as e:
            logger.warning(f"Redis cache lookup failed: {e}")
            return None

    def update(self, prompt: str, llm_string: str, return_val: RETURN_VAL_TYPE) -> None:
        """Update cache."""
        if not return_val:
            return

        # Ensure there is content.
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
            logger.warning(f"Redis cache update failed: {e}")

    def clear(self, **kwargs: Any) -> None:
        """Clear cache."""
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
            logger.info(f"LLM Redis cache cleared: deleted {deleted} entries")
        except Exception as e:
            logger.warning(f"Redis cache clear failed: {e}")


def create_llm_cache(cache_config: LLMCacheConfig) -> BaseCache | None:
    """
    Create LLM cache instance based on config.

    Config (llm.cache):
    - enabled: enable cache (default True)
    - backend: memory or redis (default memory)
    - ttl_seconds: TTL seconds (default 300)
    - max_size: max in-memory entries (default 1000)
    - redis_url: Redis URL (required when backend=redis)
    - key_prefix: Redis key prefix (default llm_cache:)
    """
    if not cache_config.enabled:
        return None

    backend = cache_config.backend.lower()

    if backend == "redis":
        if not cache_config.redis_url:
            logger.warning(
                "LLM cache backend=redis but redis_url is missing; falling back to memory cache"
            )
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
