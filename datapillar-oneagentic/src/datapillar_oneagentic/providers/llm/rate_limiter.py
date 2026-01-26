"""
LLM rate limiter.

Based on the OpenAI RPM idea:
- RPM (requests per minute) limit
- max_concurrent: max concurrent requests

Implementation:
- LangChain InMemoryRateLimiter for RPM (token bucket)
- asyncio.Semaphore for concurrency
- Rate limiters are isolated per provider

Example:
```python
from datapillar_oneagentic.providers.llm.rate_limiter import RateLimitManager

manager = RateLimitManager(config)
async with manager.acquire("openai"):
    response = await llm.ainvoke(messages)
```
"""

from __future__ import annotations

import asyncio
import logging
import threading
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from typing import TYPE_CHECKING

from langchain_core.rate_limiters import InMemoryRateLimiter

if TYPE_CHECKING:
    from datapillar_oneagentic.providers.llm.config import RateLimitConfig

logger = logging.getLogger(__name__)


@dataclass
class ProviderRateLimiter:
    """
    Rate limiter for a single provider.

    Composition:
    - InMemoryRateLimiter: RPM control (token bucket)
    - Semaphore: concurrency control
    """

    provider: str
    rpm: int
    max_concurrent: int
    _rate_limiter: InMemoryRateLimiter = field(init=False)
    _semaphore: asyncio.Semaphore = field(init=False)
    _active_requests: int = field(default=0, init=False)
    _total_requests: int = field(default=0, init=False)
    _lock: asyncio.Lock = field(default_factory=asyncio.Lock, init=False)

    def __post_init__(self):
        # Convert RPM to requests_per_second.
        # rpm=60 -> rps=1, rpm=600 -> rps=10
        requests_per_second = self.rpm / 60.0

        self._rate_limiter = InMemoryRateLimiter(
            requests_per_second=requests_per_second,
            check_every_n_seconds=0.1,
            max_bucket_size=max(10, self.rpm // 6),  # Allow 10 seconds of burst.
        )
        self._semaphore = asyncio.Semaphore(self.max_concurrent)

        logger.info(
            f"Rate limiter initialized: provider={self.provider}, "
            f"rpm={self.rpm}, max_concurrent={self.max_concurrent}"
        )

    @asynccontextmanager
    async def acquire(self) -> AsyncGenerator[None, None]:
        """
        Acquire a request permit.

        Dual limiting:
        1. Semaphore for concurrency
        2. InMemoryRateLimiter for RPM
        """
        # Acquire concurrency permit first.
        await self._semaphore.acquire()

        async with self._lock:
            self._active_requests += 1
            self._total_requests += 1

        try:
            # Then await RPM permit.
            await self._rate_limiter.aacquire()
            yield
        finally:
            self._semaphore.release()
            async with self._lock:
                self._active_requests -= 1

    @property
    def active_requests(self) -> int:
        """Current active request count."""
        return self._active_requests

    @property
    def total_requests(self) -> int:
        """Total request count."""
        return self._total_requests

    def stats(self) -> dict:
        """Get stats."""
        return {
            "provider": self.provider,
            "rpm": self.rpm,
            "max_concurrent": self.max_concurrent,
            "active_requests": self._active_requests,
            "total_requests": self._total_requests,
        }


class RateLimitManager:
    """
    Rate limit manager.

    Manages provider rate limiters, thread-safe.

    Example:
    ```python
    manager = RateLimitManager(config)
    async with manager.acquire("openai"):
        response = await llm.ainvoke(messages)
    ```
    """

    def __init__(self, config: RateLimitConfig) -> None:
        self._limiters: dict[str, ProviderRateLimiter] = {}
        self._lock = threading.Lock()
        self._config = config

    def _ensure_limiter(self, provider: str) -> ProviderRateLimiter:
        """Get or create a provider limiter."""
        provider_lower = provider.lower()

        # Double-checked locking.
        if provider_lower in self._limiters:
            return self._limiters[provider_lower]

        with self._lock:
            if provider_lower not in self._limiters:
                provider_config = self._config.get_provider_config(provider_lower)

                self._limiters[provider_lower] = ProviderRateLimiter(
                    provider=provider_lower,
                    rpm=provider_config.rpm,
                    max_concurrent=provider_config.max_concurrent,
                )

            return self._limiters[provider_lower]

    @asynccontextmanager
    async def acquire(self, provider: str) -> AsyncGenerator[None, None]:
        """
        Acquire a permit for the provider.

        If rate limiting is disabled, proceed immediately.

        Args:
            provider: provider name (openai, anthropic, glm, etc.)
        """
        if not self._config.enabled:
            yield
            return

        limiter = self._ensure_limiter(provider)
        async with limiter.acquire():
            yield

    def stats(self) -> dict:
        """Get stats for all providers."""
        return {
            "enabled": self._config.enabled,
            "providers": {
                name: limiter.stats() for name, limiter in self._limiters.items()
            },
        }

    def get_provider_stats(self, provider: str) -> dict | None:
        """Get stats for a specific provider."""
        provider_lower = provider.lower()
        if provider_lower in self._limiters:
            return self._limiters[provider_lower].stats()
        return None

    def reset(self) -> None:
        """Reset all limiters (tests only)."""
        with self._lock:
            self._limiters.clear()
