# @author Sunny
# @date 2026-01-27

"""
OneAgentic Configuration builder

from ai_model Read enabled Chat/Embedding model,and merge the business side llm/agent Configuration.DEPRECATED:- This file is only reserved for historical link compatibility
- ETL `/chat` New links no longer rely on this file
"""

from __future__ import annotations

from copy import deepcopy
from functools import lru_cache

from datapillar_oneagentic import DatapillarConfig

from src.infrastructure.keystore.crypto_service import (
    is_encrypted_ciphertext,
    local_crypto_service,
)
from src.infrastructure.repository.system.ai_model import Model
from src.infrastructure.repository.system.tenant import Tenant
from src.shared.config.runtime import get_agent_config, get_default_tenant_id, get_llm_config
from src.shared.context import get_current_tenant_code, get_current_tenant_id


def _coerce_dict(value: object) -> dict[str, object]:
    if isinstance(value, dict):
        return deepcopy(value)
    to_dict = getattr(value, "to_dict", None)
    if callable(to_dict):
        return deepcopy(to_dict())
    return {}


@lru_cache(maxsize=128)
def get_datapillar_config(
    tenant_id: int | None = None, tenant_code: str | None = None
) -> DatapillarConfig:
    resolved_tenant_id = tenant_id or get_default_tenant_id()
    resolved_tenant_code = _resolve_tenant_code(resolved_tenant_id, tenant_code)

    chat_model = Model.get_chat_default(resolved_tenant_id)
    if not chat_model:
        raise ValueError("Enabled not found Chat model,please check ai_model Configuration")

    return _build_config_from_models(
        tenant_id=resolved_tenant_id,
        tenant_code=resolved_tenant_code,
        chat_model=chat_model,
    )


def get_config_by_model(
    *,
    tenant_id: int,
    tenant_code: str | None,
    ai_model_id: int,
    provider_model_id: str,
) -> DatapillarConfig:
    if ai_model_id <= 0:
        raise ValueError("model.aiModelId Invalid")
    normalized_provider_model_id = provider_model_id.strip()
    if not normalized_provider_model_id:
        raise ValueError("model.providerModelId cannot be empty")

    resolved_tenant_code = _resolve_tenant_code(tenant_id, tenant_code)
    chat_model = Model.get_active_chat_model(
        tenant_id=tenant_id,
        ai_model_id=ai_model_id,
    )
    if not chat_model:
        raise ValueError("The specified model does not exist or is not active")
    model_provider_model_id = str(chat_model.get("provider_model_id") or "").strip()
    if model_provider_model_id != normalized_provider_model_id:
        raise ValueError("model.providerModelId with aiModelId no match")

    return _build_config_from_models(
        tenant_id=tenant_id,
        tenant_code=resolved_tenant_code,
        chat_model=chat_model,
    )


def _build_config_from_models(
    *,
    tenant_id: int,
    tenant_code: str,
    chat_model: dict[str, object],
) -> DatapillarConfig:
    embedding_model = Model.get_embedding_default(tenant_id)
    if not embedding_model:
        raise ValueError("Enabled not found Embedding model")

    dimension = embedding_model.get("embedding_dimension")
    if not dimension:
        raise ValueError("Embedding Model must be configured embedding_dimension")

    llm_config = _coerce_dict(get_llm_config())
    chat_api_key = _decrypt_key(tenant_code, chat_model.get("api_key"))
    embedding_api_key = _decrypt_key(tenant_code, embedding_model.get("api_key"))
    llm_config.update(
        {
            "provider": chat_model.get("provider_code"),
            "api_key": chat_api_key,
            "model": chat_model.get("provider_model_id"),
            "base_url": chat_model.get("base_url"),
        }
    )
    embedding_config = {
        "provider": embedding_model.get("provider_code"),
        "api_key": embedding_api_key,
        "model": embedding_model.get("provider_model_id"),
        "base_url": embedding_model.get("base_url"),
        "dimension": int(dimension),
    }

    return DatapillarConfig(
        llm=llm_config,
        embedding=embedding_config,
        agent=_coerce_dict(get_agent_config()),
    )


def _decrypt_key(tenant_code: str, encrypted_value: str | None) -> str:
    if not encrypted_value or not encrypted_value.strip():
        raise ValueError("api_key is empty")
    if not is_encrypted_ciphertext(encrypted_value):
        raise ValueError("api_key Not encrypted")
    return local_crypto_service.decrypt_key(
        tenant_code=tenant_code,
        ciphertext=encrypted_value,
    )


def _resolve_tenant_code(tenant_id: int, tenant_code: str | None) -> str:
    normalized_input = str(tenant_code or "").strip()
    if normalized_input:
        return normalized_input

    scope_tenant_code = str(get_current_tenant_code() or "").strip()
    scope_tenant_id = get_current_tenant_id()
    if scope_tenant_code and scope_tenant_id == tenant_id:
        return scope_tenant_code

    resolved = Tenant.get_code(tenant_id)
    if resolved:
        return resolved
    raise ValueError("tenant_code does not exist")
