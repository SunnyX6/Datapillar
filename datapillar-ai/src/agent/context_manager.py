"""
会话上下文管理（参考 examples/context01.py）

设计原则：
1. 使用 RemoveMessage 物理删除旧消息
2. 摘要用 SystemMessage 存储在 messages 中
3. Token + 时间窗口双重判定
"""

import time
from typing import List, Tuple
import logging

logger = logging.getLogger(__name__)
from litellm import encode

from langchain_core.messages import BaseMessage, SystemMessage, RemoveMessage
from src.integrations.llm import call_llm


def count_tokens(text: str, model_name: str = "glm-4.6") -> int:
    """
    通用 Token 计数器（支持 GPT、Claude、GLM 等）
    使用 litellm 自动加载对应模型的 tokenizer
    """
    try:
        tokens = encode(model=model_name, text=text)
        return len(tokens)
    except Exception as e:
        # 降级为估算：中文约 / 1.5，英文约 / 4，取中间值
        logger.debug(f"Token计算失败，使用估算模式: {e}")
        return len(text) // 3


class ContextManager:
    """
    上下文管理器（防止上下文爆炸）

    参考 examples/context01.py 设计：
    - 基于 Token 数量和时间窗口双重判定
    - 使用 RemoveMessage 物理删除旧消息
    - 摘要用 SystemMessage 插入 messages
    """

    def __init__(
        self,
        *,
        max_tokens: int = 10000,
        target_tokens: int = 8000,
        max_time_window: int = 7200,  # 2小时
    ):
        """
        Args:
            max_tokens: 触发压缩的 token 阈值
            target_tokens: 压缩后保留的 token 目标
            max_time_window: 时间窗口（秒），超过此时间的消息视为过期
        """
        self.max_tokens = max_tokens
        self.target_tokens = target_tokens
        self.max_time_window = max_time_window

    def _get_timestamp(self, msg: BaseMessage) -> float:
        """安全获取消息时间戳"""
        return msg.additional_kwargs.get("timestamp", time.time())

    def _count_tokens(self, text: str) -> int:
        """使用 litellm 计算 token 数量"""
        return count_tokens(text)

    def split_history_strategy(
        self,
        messages: List[BaseMessage]
    ) -> Tuple[List[BaseMessage], List[BaseMessage]]:
        """
        智能切分策略（参考 examples/context01.py）

        倒序遍历消息，收集【保留区】。
        一旦触发 [时间过期] 或 [Token溢出]，切断，剩余旧消息归入【删除区】。

        Returns:
            (to_delete, to_keep)
        """
        current_tokens = 0
        current_time = time.time()
        cutoff_index = 0

        # 保护最后1条消息（通常是刚生成的回复）
        protected_count = 1
        if len(messages) <= protected_count:
            return [], messages

        # 只处理保护区之前的消息
        candidates = messages[:-protected_count]

        for i, msg in enumerate(reversed(candidates)):
            # 跳过 SystemMessage（防止重复总结摘要）
            if isinstance(msg, SystemMessage) and "历史摘要" in str(msg.content):
                continue

            msg_len = self._count_tokens(str(msg.content))
            msg_ts = self._get_timestamp(msg)

            # 判定 A: 时间过期
            is_expired = (current_time - msg_ts) > self.max_time_window
            # 判定 B: Token 溢出
            is_overflow = (current_tokens + msg_len) > self.target_tokens

            if is_expired or is_overflow:
                cutoff_index = len(candidates) - i
                reason = "时间过期" if is_expired else "Token溢出"
                logger.info(
                    f"✂️ [ContextManager] 切分触发 [{reason}] | 当前Token积压: {current_tokens}"
                )
                break

            current_tokens += msg_len

        if cutoff_index == 0:
            return [], messages

        # 构造删除列表和保留列表
        to_delete = candidates[:cutoff_index]
        to_keep = candidates[cutoff_index:] + messages[-protected_count:]

        return to_delete, to_keep

    async def compress_if_needed(
        self,
        messages: List[BaseMessage],
    ) -> List[BaseMessage]:
        """
        如果消息超过 token 限制，进行压缩

        参考 examples/context01.py 的 compress_node 设计：
        1. 使用 split_history_strategy 切分消息
        2. 使用 LLM 生成摘要
        3. 返回 RemoveMessage + SystemMessage

        Args:
            messages: 当前的完整消息列表

        Returns:
            压缩操作列表（RemoveMessage + SystemMessage）
            如果不需要压缩，返回空列表
        """
        if not messages:
            return []

        # 计算总 token 数
        total_tokens = sum(self._count_tokens(str(m.content)) for m in messages)
        logger.debug(f"[ContextManager] 当前 messages token 数: {total_tokens}")

        # 如果未超过阈值，不压缩
        if total_tokens <= self.max_tokens:
            return []

        logger.info(
            f"[ContextManager] 触发压缩: total={total_tokens}, "
            f"threshold={self.max_tokens}"
        )

        # 切分消息
        to_delete, to_keep = self.split_history_strategy(messages)

        if not to_delete:
            return []

        logger.info(
            f"[ContextManager] 正在物理删除 {len(to_delete)} 条消息..."
        )

        # 1. 生成摘要
        text_block = "\n".join([f"{m.type}: {m.content}" for m in to_delete])
        llm = call_llm(temperature=0.3)
        from langchain_core.messages import HumanMessage as HM
        response = await llm.ainvoke([
            HM(content=f"一句话总结这些旧对话，只概括行为和意图，不包含实体信息：\n\n{text_block}")
        ])
        summary_text = response.content.strip()

        # 2. 构造 SystemMessage（带时间戳）
        summary_msg = SystemMessage(
            content=f"【历史摘要】{summary_text}",
            additional_kwargs={"timestamp": time.time()}
        )

        # 3. 构造删除指令（LangGraph 核心魔法）
        # add_messages reducer 收到 RemoveMessage 会执行物理删除
        delete_ops = []
        for m in to_delete:
            # 确保 id 是字符串类型
            msg_id = m.id if isinstance(m.id, str) else str(m.id) if m.id else None
            if msg_id:
                delete_ops.append(RemoveMessage(id=msg_id))
            else:
                logger.warning(f"[ContextManager] 跳过无效消息ID: {m.id}, type={type(m.id)}")

        logger.info(
            f"[ContextManager] 压缩完成: 删除 {len(to_delete)} 条，"
            f"保留 {len(to_keep)} 条，生成摘要"
        )

        return delete_ops + [summary_msg]
