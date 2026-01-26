"""
Vendor prompt cache utilities.

Supports provider-specific prompt caching strategies without reducing call count.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from datapillar_oneagentic.messages import Messages
OPENAI_CACHE_PROVIDERS = {"openai", "deepseek", "openrouter"}
CLAUDE_CACHE_PROVIDERS = {"anthropic", "claude"}
SYSTEM_PROMPT_KEY = "system_prompt"
DEFAULT_CACHE_CONTEXT_KEYS = ("framework_context", "knowledge_context")
DEFAULT_INCLUDE_SYSTEM_PROMPT = True


@dataclass(frozen=True, slots=True)
class VendorCachePolicy:
    provider: str
    enabled: bool
    prompt_cache_retention: str | None
    cache_control_type: str | None
    cache_control_ttl: str | None
    cache_context_keys: tuple[str, ...]
    include_system_prompt: bool

    def cache_key(self) -> tuple:
        return (
            self.enabled,
            self.prompt_cache_retention,
            self.cache_control_type,
            self.cache_control_ttl,
            self.cache_context_keys,
            self.include_system_prompt,
        )

    def is_openai_compatible(self) -> bool:
        return self.provider in OPENAI_CACHE_PROVIDERS

    def is_claude(self) -> bool:
        return self.provider in CLAUDE_CACHE_PROVIDERS


class VendorCacheManager:
    """Vendor cache policy resolver."""

    def get_policy(self, provider: str) -> VendorCachePolicy | None:
        provider_lower = provider.lower()
        prompt_cache_retention = self._resolve_prompt_cache_retention(provider_lower)
        cache_context_keys = DEFAULT_CACHE_CONTEXT_KEYS
        return VendorCachePolicy(
            provider=provider_lower,
            enabled=True,
            prompt_cache_retention=prompt_cache_retention,
            cache_control_type="ephemeral",
            cache_control_ttl=None,
            cache_context_keys=cache_context_keys,
            include_system_prompt=DEFAULT_INCLUDE_SYSTEM_PROMPT,
        )

    @staticmethod
    def _resolve_prompt_cache_retention(provider: str) -> str | None:
        if provider == "openai":
            return "in_memory"
        return None


def build_openai_extra_body(policy: VendorCachePolicy | None) -> dict[str, Any] | None:
    if policy is None or not policy.enabled:
        return None
    if not policy.is_openai_compatible():
        return None
    if not policy.prompt_cache_retention:
        return None
    return {"prompt_cache_retention": policy.prompt_cache_retention}


def apply_vendor_cache(messages: Messages, policy: VendorCachePolicy | None) -> Messages:
    if policy is None or not policy.enabled:
        return messages
    if policy.is_claude():
        return _apply_claude_cache_control(messages, policy)
    return messages


def _apply_claude_cache_control(messages: Messages, policy: VendorCachePolicy) -> Messages:
    if any(_has_cache_control(msg.metadata) for msg in messages):
        return messages

    candidate_indexes: list[int] = []
    for idx, msg in enumerate(messages):
        if msg.role != "system":
            continue
        context_key = msg.metadata.get("context_key")
        if context_key in policy.cache_context_keys:
            candidate_indexes.append(idx)
            continue
        if policy.include_system_prompt and context_key == SYSTEM_PROMPT_KEY:
            candidate_indexes.append(idx)

    if not candidate_indexes:
        return messages

    target_idx = candidate_indexes[-1]
    cache_control = _build_cache_control(policy)
    if cache_control is None:
        return messages

    updated = Messages()
    for idx, msg in enumerate(messages):
        if idx != target_idx:
            updated.append(msg)
            continue
        new_meta = dict(msg.metadata)
        new_meta["cache_control"] = cache_control
        updated.append(msg.model_copy(update={"metadata": new_meta}))
    return updated


def _build_cache_control(policy: VendorCachePolicy) -> dict[str, Any] | None:
    cache_type = policy.cache_control_type
    if not cache_type:
        return None
    cache_control: dict[str, Any] = {"type": cache_type}
    if policy.cache_control_ttl:
        cache_control["ttl"] = policy.cache_control_ttl
    return cache_control


def _has_cache_control(metadata: dict[str, Any]) -> bool:
    value = metadata.get("cache_control")
    return isinstance(value, dict) and bool(value)
