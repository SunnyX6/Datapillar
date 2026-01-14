"""
LLM 限流器

基于 OpenAI RPM 理念设计：
- RPM (Requests Per Minute): 每分钟请求数限制
- max_concurrent: 最大并发请求数限制

实现：
- 使用 LangChain InMemoryRateLimiter 实现 RPM 控制（令牌桶算法）
- 使用 asyncio.Semaphore 实现并发控制
- 按 Provider 隔离限流器

使用示例：
```python
from datapillar_oneagentic.providers.llm.rate_limiter import rate_limit_manager

# 获取限流器并执行
async with rate_limit_manager.acquire("openai"):
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
    单个 Provider 的限流器

    组合使用：
    - InMemoryRateLimiter: RPM 控制（令牌桶）
    - Semaphore: 并发控制
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
        # RPM 转换为 requests_per_second
        # rpm=60 → rps=1, rpm=600 → rps=10
        requests_per_second = self.rpm / 60.0

        self._rate_limiter = InMemoryRateLimiter(
            requests_per_second=requests_per_second,
            check_every_n_seconds=0.1,
            max_bucket_size=max(10, self.rpm // 6),  # 允许 10 秒的突发
        )
        self._semaphore = asyncio.Semaphore(self.max_concurrent)

        logger.info(
            f"限流器初始化: provider={self.provider}, "
            f"rpm={self.rpm}, max_concurrent={self.max_concurrent}"
        )

    @asynccontextmanager
    async def acquire(self) -> AsyncGenerator[None, None]:
        """
        获取请求许可

        双重限流：
        1. Semaphore 控制并发数
        2. InMemoryRateLimiter 控制 RPM
        """
        # 先获取并发许可
        await self._semaphore.acquire()

        async with self._lock:
            self._active_requests += 1
            self._total_requests += 1

        try:
            # 再等待 RPM 许可
            await self._rate_limiter.aacquire()
            yield
        finally:
            self._semaphore.release()
            async with self._lock:
                self._active_requests -= 1

    @property
    def active_requests(self) -> int:
        """当前活跃请求数"""
        return self._active_requests

    @property
    def total_requests(self) -> int:
        """总请求数"""
        return self._total_requests

    def stats(self) -> dict:
        """获取统计信息"""
        return {
            "provider": self.provider,
            "rpm": self.rpm,
            "max_concurrent": self.max_concurrent,
            "active_requests": self._active_requests,
            "total_requests": self._total_requests,
        }


class RateLimitManager:
    """
    限流管理器

    按 Provider 管理限流器，线程安全。

    使用示例：
    ```python
    # 获取管理器
    manager = get_rate_limit_manager()

    # 使用限流器
    async with manager.acquire("openai"):
        response = await llm.ainvoke(messages)

    # 查看统计
    print(manager.stats())
    ```
    """

    _instance: RateLimitManager | None = None
    _instance_lock: threading.Lock = threading.Lock()

    def __new__(cls) -> RateLimitManager:
        """单例模式"""
        if cls._instance is None:
            with cls._instance_lock:
                if cls._instance is None:
                    instance = super().__new__(cls)
                    instance._initialize()
                    cls._instance = instance
        return cls._instance

    def _initialize(self) -> None:
        """初始化"""
        self._limiters: dict[str, ProviderRateLimiter] = {}
        self._lock = threading.Lock()
        self._enabled = True

    def _get_config(self) -> RateLimitConfig:
        """获取限流配置"""
        from datapillar_oneagentic.config import get_config

        return get_config().llm.rate_limit

    def _get_or_create_limiter(self, provider: str) -> ProviderRateLimiter:
        """获取或创建 Provider 限流器"""
        provider_lower = provider.lower()

        # 双重检查锁定
        if provider_lower in self._limiters:
            return self._limiters[provider_lower]

        with self._lock:
            if provider_lower not in self._limiters:
                config = self._get_config()
                provider_config = config.get_provider_config(provider_lower)

                self._limiters[provider_lower] = ProviderRateLimiter(
                    provider=provider_lower,
                    rpm=provider_config.rpm,
                    max_concurrent=provider_config.max_concurrent,
                )

            return self._limiters[provider_lower]

    @asynccontextmanager
    async def acquire(self, provider: str) -> AsyncGenerator[None, None]:
        """
        获取指定 Provider 的请求许可

        如果限流未启用，直接放行。

        参数：
        - provider: Provider 名称（openai, anthropic, glm 等）
        """
        config = self._get_config()

        if not config.enabled:
            yield
            return

        limiter = self._get_or_create_limiter(provider)
        async with limiter.acquire():
            yield

    def stats(self) -> dict:
        """获取所有 Provider 的统计信息"""
        return {
            "enabled": self._get_config().enabled,
            "providers": {
                name: limiter.stats() for name, limiter in self._limiters.items()
            },
        }

    def get_provider_stats(self, provider: str) -> dict | None:
        """获取指定 Provider 的统计信息"""
        provider_lower = provider.lower()
        if provider_lower in self._limiters:
            return self._limiters[provider_lower].stats()
        return None

    def reset(self) -> None:
        """重置所有限流器（仅用于测试）"""
        with self._lock:
            self._limiters.clear()

    @classmethod
    def _reset_instance(cls) -> None:
        """重置单例（仅用于测试）"""
        with cls._instance_lock:
            cls._instance = None


# 全局单例
rate_limit_manager = RateLimitManager()


def get_rate_limit_manager() -> RateLimitManager:
    """获取限流管理器"""
    return rate_limit_manager
