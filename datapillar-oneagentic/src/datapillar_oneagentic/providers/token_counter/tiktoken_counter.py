"""
Tiktoken Token 计数器

基于 OpenAI tiktoken 的默认实现。

使用示例：
```python
from datapillar_oneagentic.providers.token_counter import TiktokenCounter

counter = TiktokenCounter(model="gpt-4o")
tokens = counter.count("Hello, world!")
```
"""

from __future__ import annotations

import logging

from datapillar_oneagentic.providers.token_counter.base import BaseTokenCounter

logger = logging.getLogger(__name__)


class TiktokenCounter(BaseTokenCounter):
    """
    Tiktoken Token 计数器

    基于 OpenAI tiktoken 库。
    """

    def __init__(self, model: str = "gpt-4o"):
        """
        初始化计数器

        参数：
        - model: 模型名称（用于选择编码器）
        """
        self._model = model
        self._encoding = None

    def _ensure_encoding(self):
        """确保编码器已初始化"""
        if self._encoding is None:
            try:
                import tiktoken
                try:
                    self._encoding = tiktoken.encoding_for_model(self._model)
                except KeyError:
                    # 模型不支持，使用默认编码
                    self._encoding = tiktoken.get_encoding("cl100k_base")
            except ImportError as e:
                raise ImportError(
                    "需要安装 tiktoken: pip install tiktoken"
                ) from e
        return self._encoding

    def count(self, text: str) -> int:
        """计算文本的 token 数量"""
        if not text:
            return 0
        encoding = self._ensure_encoding()
        return len(encoding.encode(text))

    def count_messages(self, messages: list[dict]) -> int:
        """
        计算消息列表的 token 数量

        使用 OpenAI 官方的计算方式。
        """
        if not messages:
            return 0

        encoding = self._ensure_encoding()

        # OpenAI 消息格式的 token 开销
        tokens_per_message = 3  # <|start|>{role}\n{content}<|end|>
        tokens_per_name = 1

        total = 0
        for message in messages:
            total += tokens_per_message
            for key, value in message.items():
                if isinstance(value, str):
                    total += len(encoding.encode(value))
                if key == "name":
                    total += tokens_per_name

        total += 3  # 回复前缀
        return total
