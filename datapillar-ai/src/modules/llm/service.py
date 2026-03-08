# @author Sunny
# @date 2026-02-19

"""
LLM Playground service.DEPRECATED:- This module is reserved for historical link compatibility
"""

from __future__ import annotations

import logging
from collections.abc import AsyncGenerator
from dataclasses import dataclass
from typing import Any

from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.providers.llm import LLMProvider
from datapillar_oneagentic.providers.llm.config import LLMConfig
from datapillar_oneagentic.providers.llm.llm import extract_thinking

from src.infrastructure.keystore.crypto_service import (
    is_encrypted_ciphertext,
    local_crypto_service,
)
from src.infrastructure.repository.system.ai_model import Model
from src.modules.llm.schemas import PlaygroundChatRequest
from src.shared.config.runtime import get_llm_config
from src.shared.exception import BadRequestException, ServiceUnavailableException

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class PlaygroundStreamDelta:
    """Playground single streaming segment."""

    text_delta: str = ""
    thinking_delta: str = ""


class LlmPlaygroundService:
    """Playground chat service(No session memory)."""

    async def stream_chat(
        self,
        *,
        tenant_id: int,
        tenant_code: str,
        payload: PlaygroundChatRequest,
    ) -> AsyncGenerator[PlaygroundStreamDelta, None]:
        ai_model_id = payload.ai_model_id
        if ai_model_id <= 0:
            raise BadRequestException("aiModelId Invalid")
        message = self._normalize_required(payload.message, "message cannot be empty")

        model = Model.get_active_chat_model(
            tenant_id=tenant_id,
            ai_model_id=ai_model_id,
        )
        if not model:
            raise BadRequestException("Model does not exist,Not enabled or not connected")

        decrypted_api_key = await self._decrypt_key(
            tenant_code=tenant_code,
            encrypted_value=model.get("api_key"),
        )

        llm_provider = self._build_llm_provider(
            model=model,
            api_key=decrypted_api_key,
            thinking_enabled=payload.model_options.thinking_enabled,
        )

        llm = llm_provider(
            temperature=payload.model_options.temperature,
            top_p=payload.model_options.top_p,
            streaming=True,
        )
        messages = self._build_messages(
            message=message,
            system_instruction=payload.model_options.system_instruction,
        )

        text_buffer = ""
        thinking_buffer = ""

        async for chunk in llm.astream(messages):
            thinking_chunk = self._extract_chunk_thinking(chunk)
            thinking_delta = self._resolve_stream_delta(
                current_chunk=thinking_chunk,
                accumulated=thinking_buffer,
            )
            if thinking_delta:
                thinking_buffer += thinking_delta
                yield PlaygroundStreamDelta(thinking_delta=thinking_delta)

            text_chunk = self._extract_chunk_text(chunk)
            text_delta = self._resolve_stream_delta(
                current_chunk=text_chunk,
                accumulated=text_buffer,
            )
            if text_delta:
                text_buffer += text_delta
                yield PlaygroundStreamDelta(text_delta=text_delta)

    def _build_llm_provider(
        self,
        *,
        model: dict[str, Any],
        api_key: str,
        thinking_enabled: bool,
    ) -> LLMProvider:
        llm_payload = get_llm_config()
        llm_payload.update(
            {
                "provider": str(model.get("provider_code") or "").lower(),
                "api_key": api_key,
                "model": model.get("provider_model_id"),
                "base_url": model.get("base_url"),
                "enable_thinking": thinking_enabled,
            }
        )
        llm_config = LLMConfig.model_validate(llm_payload)
        return LLMProvider(llm_config)

    async def _decrypt_key(self, *, tenant_code: str, encrypted_value: str | None) -> str:
        if not encrypted_value or not encrypted_value.strip():
            raise BadRequestException("Model not configured API Key")
        if not is_encrypted_ciphertext(encrypted_value):
            raise BadRequestException("model API Key Invalid encryption format")

        try:
            return await local_crypto_service.decrypt_key_async(
                tenant_code=tenant_code,
                ciphertext=encrypted_value,
            )
        except BaseException as exc:  # pragma:no cover - defense branch
            logger.error(
                "Decrypt the model API Key failed:tenantCode=%s modelKeyPresent=%s",
                tenant_code,
                bool(encrypted_value),
            )
            raise ServiceUnavailableException("model API Key Decryption failed", cause=exc) from exc

    def _extract_chunk_text(self, chunk: Any) -> str:
        if isinstance(chunk, Message):
            return chunk.content or ""

        content = getattr(chunk, "content", None)
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            return self._extract_text_from_blocks(content)

        if isinstance(chunk, dict):
            for key in ("content", "text", "delta"):
                value = chunk.get(key)
                if isinstance(value, str):
                    return value
                if isinstance(value, list):
                    return self._extract_text_from_blocks(value)

        return ""

    def _extract_chunk_thinking(self, chunk: Any) -> str:
        thinking = extract_thinking(chunk)
        if isinstance(thinking, str) and thinking:
            return thinking

        if isinstance(chunk, dict):
            for key in ("reasoning_content", "reasoning", "thinking"):
                value = chunk.get(key)
                if isinstance(value, str) and value:
                    return value
            content = chunk.get("content")
            if isinstance(content, list):
                return self._extract_thinking_from_blocks(content)

        content = getattr(chunk, "content", None)
        if isinstance(content, list):
            return self._extract_thinking_from_blocks(content)

        return ""

    def _build_messages(self, *, message: str, system_instruction: str | None) -> Messages:
        normalized_system_instruction = (system_instruction or "").strip()
        if normalized_system_instruction:
            return Messages([Message.system(normalized_system_instruction), Message.user(message)])
        return Messages([Message.user(message)])

    def _extract_text_from_blocks(self, blocks: list[Any]) -> str:
        texts: list[str] = []
        for block in blocks:
            if isinstance(block, str):
                texts.append(block)
                continue
            if not isinstance(block, dict):
                continue
            if block.get("type") == "text" and isinstance(block.get("text"), str):
                texts.append(block["text"])
        return "".join(texts)

    def _extract_thinking_from_blocks(self, blocks: list[Any]) -> str:
        thinking_parts: list[str] = []
        for block in blocks:
            if not isinstance(block, dict):
                continue
            if block.get("type") != "thinking":
                continue
            thinking = block.get("thinking")
            if isinstance(thinking, str) and thinking:
                thinking_parts.append(thinking)
        return "".join(thinking_parts)

    def _resolve_stream_delta(self, *, current_chunk: str, accumulated: str) -> str:
        if not current_chunk:
            return ""
        if not accumulated:
            return current_chunk
        if current_chunk.startswith(accumulated):
            return current_chunk[len(accumulated) :]
        if accumulated.endswith(current_chunk):
            return ""
        return current_chunk

    def _normalize_required(self, value: str | None, message: str) -> str:
        if not value or not value.strip():
            raise BadRequestException(message)
        return value.strip()


llm_playground_service = LlmPlaygroundService()
