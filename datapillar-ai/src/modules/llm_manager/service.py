# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-05

"""LLM 模型管理服务层。"""

from __future__ import annotations

import json
import logging
from decimal import Decimal
from typing import Any

from datapillar_oneagentic.messages import Messages
from datapillar_oneagentic.providers.llm import LLMProvider
from datapillar_oneagentic.providers.llm.config import LLMConfig, Provider as LLMProviderEnum

from src.infrastructure.crypto.key_crypto import encrypt_api_key
from src.infrastructure.repository.system.ai_model import Model as AiModelRepository
from src.infrastructure.repository.system.ai_provider import Provider as AiProviderRepository
from src.infrastructure.repository.system.tenant import Tenant as TenantRepository
from src.modules.llm_manager.schemas import ModelCreateRequest, ModelType, ModelUpdateRequest

logger = logging.getLogger(__name__)


class LlmManagerError(Exception):
    """LLM 模型管理基础异常。"""


class InvalidParamError(LlmManagerError):
    """参数错误。"""


class NotFoundError(LlmManagerError):
    """资源不存在。"""


class ConflictError(LlmManagerError):
    """资源冲突。"""


class ConnectError(LlmManagerError):
    """连接验证失败。"""


class LlmManagerService:
    def __init__(self) -> None:
        self._model_repo = AiModelRepository
        self._provider_repo = AiProviderRepository
        self._tenant_repo = TenantRepository

    def list_providers(self) -> list[dict[str, Any]]:
        rows = self._provider_repo.list_all()
        supported = set(LLMProviderEnum.list_supported())
        providers: list[dict[str, Any]] = []
        for row in rows:
            code = (row.get("code") or "").lower()
            if code not in supported:
                logger.warning("跳过不支持的供应商: %s", code)
                continue
            providers.append(self._normalize_provider(row))
        return providers

    def list_models(
        self,
        *,
        limit: int,
        offset: int,
        tenant_id: int,
        keyword: str | None = None,
        provider_code: str | None = None,
        model_type: str | None = None,
    ) -> tuple[list[dict[str, Any]], int]:
        provider_code = (provider_code or "").lower().strip() or None
        rows, total = self._model_repo.list_models(
            limit=limit,
            offset=offset,
            tenant_id=tenant_id,
            keyword=keyword,
            provider_code=provider_code,
            model_type=model_type,
        )
        return [self._normalize_model(row) for row in rows], total

    def get_model(self, model_id: int, tenant_id: int) -> dict[str, Any] | None:
        row = self._model_repo.get_detail(model_id, tenant_id)
        return self._normalize_model(row) if row else None

    def create_model(self, *, user_id: int, tenant_id: int, payload: ModelCreateRequest) -> dict[str, Any]:
        provider_code = (payload.provider_code or "").lower().strip()
        self._ensure_provider_supported(provider_code)

        provider = self._provider_repo.get_by_code(provider_code)
        if not provider:
            raise InvalidParamError("供应商不存在")

        existed = self._model_repo.get_by_model_id(payload.model_id, tenant_id)
        if existed:
            raise ConflictError("model_id 已存在")

        if payload.model_type == ModelType.EMBEDDINGS and not payload.embedding_dimension:
            raise InvalidParamError("embedding_dimension 不能为空")

        tags_json = self._dump_json_list(payload.tags)
        base_url = self._normalize_text(payload.base_url)

        model_id = self._model_repo.create(
            {
                "tenant_id": tenant_id,
                "model_id": payload.model_id,
                "name": payload.name,
                "provider_id": int(provider["id"]),
                "model_type": payload.model_type.value,
                "description": payload.description,
                "tags": tags_json,
                "context_tokens": payload.context_tokens,
                "input_price_usd": payload.input_price_usd,
                "output_price_usd": payload.output_price_usd,
                "embedding_dimension": payload.embedding_dimension,
                "api_key": None,
                "base_url": base_url,
                "status": "CONNECT",
                "created_by": user_id,
                "updated_by": user_id,
            }
        )

        row = self._model_repo.get_detail(model_id, tenant_id)
        if not row:
            raise NotFoundError("模型创建失败")
        return self._normalize_model(row)

    def update_model(
        self,
        *,
        user_id: int,
        tenant_id: int,
        model_id: int,
        payload: ModelUpdateRequest,
    ) -> dict[str, Any]:
        fields = {k: v for k, v in payload.model_dump().items() if v is not None}
        if not fields:
            raise InvalidParamError("没有可更新字段")

        if "tags" in fields:
            fields["tags"] = self._dump_json_list(fields["tags"])
        if "base_url" in fields:
            fields["base_url"] = self._normalize_text(fields["base_url"])
        fields["updated_by"] = user_id

        updated = self._model_repo.update(model_id, tenant_id, fields)
        if updated == 0:
            raise NotFoundError("模型不存在")

        row = self._model_repo.get_detail(model_id, tenant_id)
        if not row:
            raise NotFoundError("模型不存在")
        return self._normalize_model(row)

    def delete_model(self, *, tenant_id: int, model_id: int) -> int:
        deleted = self._model_repo.delete(model_id, tenant_id)
        if deleted == 0:
            raise NotFoundError("模型不存在")
        return deleted

    async def connect_model(
        self,
        *,
        user_id: int,
        tenant_id: int,
        model_id: int,
        api_key: str,
        base_url: str | None,
    ) -> dict[str, Any]:
        row = self._model_repo.get_detail(model_id, tenant_id)
        if not row:
            raise NotFoundError("模型不存在")

        provider_code = (row.get("provider_code") or "").lower().strip()
        self._ensure_provider_supported(provider_code)

        api_key = (api_key or "").strip()
        if not api_key:
            raise InvalidParamError("api_key 不能为空")

        resolved_base_url = self._normalize_text(base_url) or self._normalize_text(row.get("base_url"))
        if not resolved_base_url:
            raise InvalidParamError("base_url 不能为空")

        await self._validate_connection(
            provider_code=provider_code,
            model_id=row.get("model_id"),
            api_key=api_key,
            base_url=resolved_base_url,
        )

        encrypted_key = self._encrypt_api_key(tenant_id, api_key)

        self._model_repo.update_connection(
            model_id,
            tenant_id,
            {
                "api_key": encrypted_key,
                "base_url": resolved_base_url,
                "status": "ACTIVE",
                "updated_by": user_id,
            },
        )

        return {"connected": True, "has_api_key": True}

    async def _validate_connection(
        self,
        *,
        provider_code: str,
        model_id: str | None,
        api_key: str,
        base_url: str,
    ) -> None:
        if not model_id:
            raise InvalidParamError("model_id 不能为空")
        try:
            config = LLMConfig(
                provider=provider_code,
                api_key=api_key,
                model=model_id,
                base_url=base_url,
            )
            llm = LLMProvider(config).get_llm()
            messages = Messages().user("hello")
            await llm.ainvoke(messages)
        except Exception as exc:
            logger.error(
                "LLM 连接验证失败: provider=%s, model_id=%s, error=%s",
                provider_code,
                model_id,
                exc,
                exc_info=True,
            )
            raise ConnectError("连接失败，请检查 API Key/Base URL") from exc

    def _ensure_provider_supported(self, provider_code: str) -> None:
        supported = set(LLMProviderEnum.list_supported())
        if provider_code not in supported:
            raise InvalidParamError(f"不支持的供应商: {provider_code}")

    def _encrypt_api_key(self, tenant_id: int, api_key: str) -> str:
        if tenant_id <= 0:
            raise InvalidParamError("tenant_id 无效")
        public_key = self._tenant_repo.get_encrypt_public_key(tenant_id)
        if not public_key:
            raise InvalidParamError("租户公钥未初始化")
        try:
            return encrypt_api_key(public_key, api_key)
        except ValueError as exc:
            raise InvalidParamError(str(exc)) from exc

    def _normalize_provider(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": row.get("id"),
            "code": row.get("code"),
            "name": row.get("name"),
            "base_url": row.get("base_url"),
            "model_ids": self._parse_json_list(row.get("model_ids")),
        }

    def _normalize_model(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": row.get("id"),
            "model_id": row.get("model_id"),
            "name": row.get("name"),
            "provider_id": row.get("provider_id"),
            "provider_code": row.get("provider_code"),
            "provider_name": row.get("provider_name"),
            "model_type": row.get("model_type"),
            "description": row.get("description"),
            "tags": self._parse_json_list(row.get("tags")),
            "context_tokens": row.get("context_tokens"),
            "input_price_usd": self._stringify_decimal(row.get("input_price_usd")),
            "output_price_usd": self._stringify_decimal(row.get("output_price_usd")),
            "embedding_dimension": row.get("embedding_dimension"),
            "base_url": row.get("base_url"),
            "status": row.get("status"),
            "has_api_key": bool(row.get("api_key")),
            "created_by": row.get("created_by"),
            "updated_by": row.get("updated_by"),
            "created_at": row.get("created_at"),
            "updated_at": row.get("updated_at"),
        }

    @staticmethod
    def _parse_json_list(value: object) -> list[Any]:
        if value is None:
            return []
        if isinstance(value, list):
            return value
        if isinstance(value, (str, bytes)):
            raw = value.decode() if isinstance(value, bytes) else value
            if not raw:
                return []
            try:
                parsed = json.loads(raw)
            except json.JSONDecodeError:
                return []
            return parsed if isinstance(parsed, list) else []
        return []

    @staticmethod
    def _dump_json_list(value: list[Any] | None) -> str | None:
        if value is None:
            return None
        return json.dumps(value, ensure_ascii=False)

    @staticmethod
    def _normalize_text(value: str | None) -> str | None:
        if value is None:
            return None
        trimmed = value.strip()
        return trimmed or None

    @staticmethod
    def _stringify_decimal(value: Any) -> str | None:
        if value is None:
            return None
        if isinstance(value, Decimal):
            return str(value)
        return str(value)
