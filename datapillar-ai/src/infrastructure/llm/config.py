"""
OneAgentic 配置构建器

从 ai_model 读取启用的 Chat/Embedding 模型，并合并业务侧 llm/agent 配置。
"""

from __future__ import annotations

from copy import deepcopy
from functools import lru_cache

from datapillar_oneagentic import DatapillarConfig

from src.infrastructure.repository.system.ai_model import Model
from src.shared.config.settings import settings


def _coerce_dict(value: object) -> dict[str, object]:
    if isinstance(value, dict):
        return deepcopy(value)
    to_dict = getattr(value, "to_dict", None)
    if callable(to_dict):
        return deepcopy(to_dict())
    return {}


@lru_cache(maxsize=1)
def get_datapillar_config() -> DatapillarConfig:
    chat_model = Model.get_chat_default()
    if not chat_model:
        raise ValueError("未找到启用的 Chat 模型，请检查 ai_model 配置")

    embedding_model = Model.get_embedding_default()
    if not embedding_model:
        raise ValueError("未找到启用的 Embedding 模型（scope=SYSTEM）")

    dimension = embedding_model.get("embedding_dimension")
    if not dimension:
        raise ValueError("Embedding 模型必须配置 embedding_dimension")

    llm_config = _coerce_dict(settings.get("llm", {}) or {})
    llm_config.update(
        {
            "provider": chat_model.get("provider"),
            "api_key": chat_model.get("api_key"),
            "model": chat_model.get("model_name"),
            "base_url": chat_model.get("base_url"),
        }
    )
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
        agent=_coerce_dict(settings.get("agent", {}) or {}),
    )
