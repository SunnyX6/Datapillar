"""
Token 计数器模块

提供 token 计数功能，用于：
- 上下文预算控制
- 记忆裁剪

使用示例：
```python
from datapillar_oneagentic.providers.token_counter import get_token_counter

counter = get_token_counter()
tokens = counter.count("Hello, world!")
```
"""

from datapillar_oneagentic.providers.token_counter.base import BaseTokenCounter
from datapillar_oneagentic.providers.token_counter.tiktoken_counter import TiktokenCounter

# 默认计数器
_default_counter: BaseTokenCounter | None = None


def get_token_counter() -> BaseTokenCounter:
    """
    获取 Token 计数器

    优先级：
    1. 配置中指定的 token_counter
    2. 默认的 tiktoken 计数器

    返回：
    - Token 计数器实例
    """
    from datapillar_oneagentic.config import datapillar

    # 检查是否有自定义 token counter
    custom_counter = datapillar.model_extra.get("token_counter") if datapillar.model_extra else None
    if custom_counter is not None:
        return custom_counter

    global _default_counter
    if _default_counter is None:
        _default_counter = TiktokenCounter()
    return _default_counter


def reset_token_counter() -> None:
    """重置默认计数器（仅用于测试）"""
    global _default_counter
    _default_counter = None


__all__ = [
    "BaseTokenCounter",
    "TiktokenCounter",
    "get_token_counter",
    "reset_token_counter",
]
