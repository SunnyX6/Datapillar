"""
弹性机制统一配置

所有弹性相关参数的单一入口，从 settings.toml 读取。
"""

from dataclasses import dataclass
from typing import Any

from src.shared.config.settings import settings


@dataclass(frozen=True)
class ResilienceConfig:
    """
    弹性配置（不可变）

    所有智能体、所有场景使用同一套参数。
    """

    # 重试配置
    max_retries: int
    initial_delay_ms: int
    max_delay_ms: int
    exponential_base: float
    jitter: bool

    # 超时配置
    llm_timeout_seconds: float
    tool_timeout_seconds: float

    # Agent 执行配置
    max_iterations: int  # LLM 调用轮次上限

    # 熔断配置
    circuit_failure_threshold: int
    circuit_recovery_timeout_seconds: float

    def calculate_delay(self, attempt: int) -> float:
        """
        计算第 N 次重试的延迟（秒）

        使用指数退避 + 可选抖动。
        """
        import random

        delay_ms = self.initial_delay_ms * (self.exponential_base**attempt)
        delay_ms = min(delay_ms, self.max_delay_ms)

        if self.jitter:
            # ±25% 抖动，避免惊群效应
            jitter_range = delay_ms * 0.25
            delay_ms += random.uniform(-jitter_range, jitter_range)

        return max(delay_ms / 1000.0, 0.0)

    @classmethod
    def from_settings(cls) -> "ResilienceConfig":
        """从 settings.toml 读取配置"""
        resilience: Any = settings.get("resilience", {})

        return cls(
            # 重试
            max_retries=int(resilience.get("max_retries", 3)),
            initial_delay_ms=int(resilience.get("initial_delay_ms", 500)),
            max_delay_ms=int(resilience.get("max_delay_ms", 30000)),
            exponential_base=float(resilience.get("exponential_base", 2.0)),
            jitter=bool(resilience.get("jitter", True)),
            # 超时
            llm_timeout_seconds=float(resilience.get("llm_timeout_seconds", 120)),
            tool_timeout_seconds=float(resilience.get("tool_timeout_seconds", 30)),
            # Agent 执行
            max_iterations=int(resilience.get("max_iterations", 10)),
            # 熔断
            circuit_failure_threshold=int(resilience.get("circuit_failure_threshold", 5)),
            circuit_recovery_timeout_seconds=float(
                resilience.get("circuit_recovery_timeout_seconds", 60)
            ),
        )


# 全局单例
_config: ResilienceConfig | None = None


def get_resilience_config() -> ResilienceConfig:
    """获取弹性配置（懒加载单例）"""
    global _config
    if _config is None:
        _config = ResilienceConfig.from_settings()
    return _config
