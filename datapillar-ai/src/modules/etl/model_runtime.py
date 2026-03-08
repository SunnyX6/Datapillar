# @author Sunny
# @date 2026-02-26

"""ETL Model runtime configuration build(Force explicit model)."""

from __future__ import annotations

from copy import deepcopy

from datapillar_oneagentic import DatapillarConfig

from src.infrastructure.repository.system.ai_model_new import ModelNew
from src.shared.config.runtime import get_agent_config, get_llm_config
from src.shared.exception import BadRequestException, ConflictException, ForbiddenException


def _coerce_dict(value: object) -> dict[str, object]:
    if isinstance(value, dict):
        return deepcopy(value)
    to_dict = getattr(value, "to_dict", None)
    if callable(to_dict):
        return deepcopy(to_dict())
    return {}


def build_etl_datapillar_config(
    *,
    tenant_id: int,
    user_id: int,
    tenant_code: str,
    ai_model_id: int,
    provider_model_id: str,
) -> DatapillarConfig:
    normalized_provider_model_id = provider_model_id.strip()
    if ai_model_id <= 0:
        raise BadRequestException("model.aiModelId Invalid")
    if user_id <= 0:
        raise BadRequestException("userId Invalid")
    if not normalized_provider_model_id:
        raise BadRequestException("model.providerModelId cannot be empty")

    chat_model = ModelNew.get_active_chat_model_by_id(
        tenant_id=tenant_id,
        user_id=user_id,
        ai_model_id=ai_model_id,
    )
    if not chat_model:
        raise ForbiddenException("The specified model is not authorized or unavailable")

    db_provider_model_id = str(chat_model.get("provider_model_id") or "").strip()
    if db_provider_model_id != normalized_provider_model_id:
        raise ConflictException("model.providerModelId with aiModelId no match")

    embedding_model = ModelNew.get_active_embedding_default(tenant_id=tenant_id)
    if not embedding_model:
        raise BadRequestException("Enabled not found Embedding model")

    dimension = embedding_model.get("embedding_dimension")
    if not dimension:
        raise BadRequestException("Embedding Model must be configured embedding_dimension")

    chat_api_key = ModelNew.decrypt_key(
        tenant_code=tenant_code,
        encrypted_value=chat_model.get("api_key"),
    )
    embedding_api_key = ModelNew.decrypt_key(
        tenant_code=tenant_code,
        encrypted_value=embedding_model.get("api_key"),
    )

    llm_config = _coerce_dict(get_llm_config())
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
