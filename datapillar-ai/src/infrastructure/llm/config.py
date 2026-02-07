# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
OneAgentic 配置构建器

从 ai_model 读取启用的 Chat/Embedding 模型，并合并业务侧 llm/agent 配置。
"""

from __future__ import annotations

from copy import deepcopy
from functools import lru_cache

from datapillar_oneagentic import DatapillarConfig

from src.infrastructure.crypto.key_crypto import decrypt_api_key, is_encrypted
from src.infrastructure.keystore import get_key_storage
from src.infrastructure.repository.system.ai_model import Model
from src.shared.config.runtime import get_agent_config, get_default_tenant_id, get_llm_config


def _coerce_dict(value: object) -> dict[str, object]:
    if isinstance(value, dict):
        return deepcopy(value)
    to_dict = getattr(value, "to_dict", None)
    if callable(to_dict):
        return deepcopy(to_dict())
    return {}


@lru_cache(maxsize=128)
def get_datapillar_config(tenant_id: int | None = None) -> DatapillarConfig:
    resolved_tenant_id = tenant_id or get_default_tenant_id()

    chat_model = Model.get_chat_default(resolved_tenant_id)
    if not chat_model:
        raise ValueError("未找到启用的 Chat 模型，请检查 ai_model 配置")

    embedding_model = Model.get_embedding_default(resolved_tenant_id)
    if not embedding_model:
        raise ValueError("未找到启用的 Embedding 模型")

    dimension = embedding_model.get("embedding_dimension")
    if not dimension:
        raise ValueError("Embedding 模型必须配置 embedding_dimension")

    llm_config = _coerce_dict(get_llm_config())
    chat_api_key = _decrypt_api_key(resolved_tenant_id, chat_model.get("api_key"))
    embedding_api_key = _decrypt_api_key(resolved_tenant_id, embedding_model.get("api_key"))
    llm_config.update(
        {
            "provider": chat_model.get("provider_code"),
            "api_key": chat_api_key,
            "model": chat_model.get("model_id"),
            "base_url": chat_model.get("base_url"),
        }
    )
    embedding_config = {
        "provider": embedding_model.get("provider_code"),
        "api_key": embedding_api_key,
        "model": embedding_model.get("model_id"),
        "base_url": embedding_model.get("base_url"),
        "dimension": int(dimension),
    }

    return DatapillarConfig(
        llm=llm_config,
        embedding=embedding_config,
        agent=_coerce_dict(get_agent_config()),
    )


def _decrypt_api_key(tenant_id: int, encrypted_value: str | None) -> str:
    if not encrypted_value or not encrypted_value.strip():
        raise ValueError("api_key 为空")
    if not is_encrypted(encrypted_value):
        raise ValueError("api_key 未加密")
    private_key = get_key_storage().load_private_key(tenant_id)
    return decrypt_api_key(private_key, encrypted_value)
