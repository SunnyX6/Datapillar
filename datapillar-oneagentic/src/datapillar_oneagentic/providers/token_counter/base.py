"""
Token 计数器抽象基类
"""

from __future__ import annotations

from abc import ABC, abstractmethod


class BaseTokenCounter(ABC):
    """
    Token 计数器抽象基类
    """

    @abstractmethod
    def count(self, text: str) -> int:
        """
        计算文本的 token 数量

        参数：
        - text: 输入文本

        返回：
        - token 数量
        """
        ...

    @abstractmethod
    def count_messages(self, messages: list[dict]) -> int:
        """
        计算消息列表的 token 数量

        参数：
        - messages: 消息列表（OpenAI 格式）

        返回：
        - token 数量
        """
        ...
