"""
OneAgentic 配置构建器

从 ai_model 读取默认 Chat/Embedding 模型，并注入 Redis 存储与缓存配置。
"""

from __future__ import annotations

from functools import lru_cache
from urllib.parse import quote

from datapillar_oneagentic import DatapillarConfig

from src.infrastructure.repository.system.ai_model import Model
from src.shared.config.settings import settings


def _build_redis_url() -> str:
    host = settings.redis_host
    port = settings.redis_port
    db = settings.redis_db
    password = settings.redis_password or ""
    if password:
        encoded = quote(str(password), safe="")
        return f"redis://:{encoded}@{host}:{port}/{db}"
    return f"redis://{host}:{port}/{db}"


def _build_llm_cache(redis_url: str) -> dict[str, object]:
    llm_cache = settings.get("llm_cache", {})
    enabled = bool(llm_cache.get("enabled", True))
    ttl_seconds = int(llm_cache.get("ttl_seconds", 300))
    key_prefix = str(llm_cache.get("key_prefix", "llm_cache:"))
    return {
        "enabled": enabled,
        "backend": "redis",
        "redis_url": redis_url,
        "ttl_seconds": ttl_seconds,
        "key_prefix": key_prefix,
    }


def _build_agent_storage(redis_url: str) -> dict[str, object]:
    agent_settings = settings.get("agent", {}) or {}
    checkpointer_settings = agent_settings.get("checkpointer", {}) or {}
    ttl_minutes = checkpointer_settings.get("ttl_minutes")

    checkpointer_config: dict[str, object] = {
        "type": "redis",
        "url": redis_url,
    }
    if ttl_minutes:
        checkpointer_config["ttl_minutes"] = float(ttl_minutes)

    return {
        "checkpointer": checkpointer_config,
        "deliverable_store": {
            "type": "redis",
            "url": redis_url,
        },
    }


@lru_cache(maxsize=1)
def get_datapillar_config() -> DatapillarConfig:
    chat_model = Model.get_chat_default()
    if not chat_model:
        raise ValueError("未找到启用的默认 Chat 模型，请检查 ai_model 配置")

    embedding_model = Model.get_embedding_default()
    if not embedding_model:
        raise ValueError("未找到启用的默认 Embedding 模型（scope=SYSTEM）")

    dimension = embedding_model.get("embedding_dimension")
    if not dimension:
        raise ValueError("Embedding 模型必须配置 embedding_dimension")

    redis_url = _build_redis_url()
    llm_config = {
        "provider": chat_model.get("provider"),
        "api_key": chat_model.get("api_key"),
        "model": chat_model.get("model_name"),
        "base_url": chat_model.get("base_url"),
        "cache": _build_llm_cache(redis_url),
    }
    embedding_config = {
        "provider": embedding_model.get("provider"),
        "api_key": embedding_model.get("api_key"),
        "model": embedding_model.get("model_name"),
        "base_url": embedding_model.get("base_url"),
        "dimension": int(dimension),
    }

    return DatapillarConfig(
        llm=llm_config,
        embedding=embedding_config,
        agent=_build_agent_storage(redis_url),
    )
