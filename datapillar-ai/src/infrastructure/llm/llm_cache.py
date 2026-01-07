"""
LLM 精确缓存（Redis 存储）

设计原则：
- 精确匹配：相同内容 = 命中，任何差异 = 不命中
- 规范化处理：去除 LangChain 消息中的动态 ID，只保留实际内容
- Redis 存储：支持 TTL，分布式友好
- 简单可靠：无 embedding、无相似度计算、无误判
- 同步接口：LangChain BaseCache 要求同步，使用同步 Redis 客户端
"""

from __future__ import annotations

import hashlib
import json
import logging
from typing import Any

import redis
from langchain_core.caches import RETURN_VAL_TYPE, BaseCache
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, Generation

from src.shared.config.settings import settings

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


class LLMCache(BaseCache):
    """
    LLM 精确缓存（Redis 存储，同步接口）

    特点：
    - 精确匹配：相同内容才命中
    - 规范化处理：去除动态 ID
    - Redis 存储：支持 TTL
    - 同步接口：兼容 LangChain BaseCache
    - 简单可靠：无误判
    """

    def __init__(
        self,
        *,
        ttl_seconds: int = 300,
        key_prefix: str = "llm_cache:",
    ):
        self.ttl_seconds = ttl_seconds
        self.key_prefix = key_prefix
        self._redis: redis.Redis | None = None
        logger.info(f"LLM 缓存初始化: ttl={ttl_seconds}s, prefix={key_prefix}")

    def _get_redis(self) -> redis.Redis:
        """获取同步 Redis 客户端（延迟初始化）"""
        if self._redis is None:
            self._redis = redis.Redis(
                host=settings.redis_host,
                port=settings.redis_port,
                db=settings.redis_db,
                password=settings.redis_password or None,
                decode_responses=True,
            )
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
            if result is not None:
                logger.info(f"[LLM 缓存命中] key={cache_key[:20]}...")
            return result

        except Exception as e:
            logger.warning(f"缓存查询失败: {e}")
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
            logger.debug(f"[LLM 缓存写入] key={cache_key[:20]}..., ttl={self.ttl_seconds}s")

        except Exception as e:
            logger.warning(f"缓存写入失败: {e}")

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
            logger.info(f"LLM 缓存已清空: 删除 {deleted} 条记录")
        except Exception as e:
            logger.warning(f"缓存清空失败: {e}")


def create_llm_cache() -> LLMCache | None:
    """
    创建 LLM 缓存实例（从配置读取）

    配置项（settings.toml [default.llm_cache]）：
    - enabled: 是否启用缓存（默认 true）
    - ttl_seconds: TTL 秒（默认 300）
    - key_prefix: Redis key 前缀（默认 llm_cache:）
    """
    cache_config = getattr(settings, "llm_cache", None)

    if cache_config is None:
        return LLMCache(ttl_seconds=300, key_prefix="llm_cache:")

    enabled = getattr(cache_config, "enabled", True)
    if not enabled:
        logger.info("LLM 缓存已禁用（llm_cache.enabled=false）")
        return None

    ttl_seconds = int(getattr(cache_config, "ttl_seconds", 300))
    key_prefix = str(getattr(cache_config, "key_prefix", "llm_cache:"))

    return LLMCache(ttl_seconds=ttl_seconds, key_prefix=key_prefix)
