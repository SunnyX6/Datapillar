"""
上下文压缩器

直接操作 LangGraph 的 messages 列表，将历史消息压缩为摘要。

压缩流程：
1. 保留最近 N 条消息 + 用户消息
2. 将其他消息压缩为摘要
3. 返回压缩后的 messages 列表

触发时机：由 ContextLengthExceededError 异常触发，不再主动检查 token。
"""

from __future__ import annotations

import logging
from typing import Any

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)

from datapillar_oneagentic.context.compaction.compact_policy import CompactPolicy, CompactResult

logger = logging.getLogger(__name__)


class Compactor:
    """
    上下文压缩器

    直接操作 LangGraph 的 messages 列表，包括：
    - 调用 LLM 生成摘要
    - 返回压缩后的 messages
    """

    def __init__(self, llm: Any, policy: CompactPolicy | None = None):
        """
        初始化压缩器

        Args:
            llm: LLM 实例
            policy: 压缩策略
        """
        self.llm = llm
        self.policy = policy or CompactPolicy()

    async def compact(
        self,
        messages: list[BaseMessage],
    ) -> tuple[list[BaseMessage], CompactResult]:
        """
        执行压缩

        Args:
            messages: 原始消息列表

        Returns:
            (压缩后的 messages, CompactResult)
        """
        if not messages:
            return messages, CompactResult.no_action("没有消息")

        # 分类消息：保留 vs 压缩
        keep_messages, compress_messages = self._classify_messages(messages)

        if not compress_messages:
            return messages, CompactResult.no_action("没有可压缩的消息")

        # 生成压缩摘要
        try:
            summary = await self._generate_summary(compress_messages)
        except Exception as e:
            logger.error(f"压缩失败: {e}", exc_info=True)
            return messages, CompactResult.failed(str(e))

        # 构建压缩后的 messages：摘要 + 保留的消息
        compressed_messages = [
            SystemMessage(content=f"[历史摘要]\n{summary}"),
            *keep_messages,
        ]

        logger.info(
            f"📦 压缩完成: {len(compress_messages)} 条 → 摘要，"
            f"保留 {len(keep_messages)} 条"
        )

        return compressed_messages, CompactResult(
            success=True,
            summary=summary,
            kept_count=len(keep_messages),
            removed_count=len(compress_messages),
        )

    def _classify_messages(
        self,
        messages: list[BaseMessage],
    ) -> tuple[list[BaseMessage], list[BaseMessage]]:
        """
        分类消息

        规则：
        - 最近 min_keep_entries 条消息始终保留
        - HumanMessage（用户消息）始终保留
        - 其他消息压缩
        """
        min_keep = self.policy.get_min_keep_entries()

        if len(messages) <= min_keep:
            return messages.copy(), []

        # 最近的消息始终保留
        recent_messages = messages[-min_keep:]
        older_messages = messages[:-min_keep]

        keep_messages = []
        compress_messages = []

        for msg in older_messages:
            # 用户消息始终保留
            if isinstance(msg, (HumanMessage, SystemMessage)):
                keep_messages.append(msg)
            else:
                compress_messages.append(msg)

        # 合并：保留的 + 最近的
        keep_messages.extend(recent_messages)

        return keep_messages, compress_messages

    async def _generate_summary(self, messages: list[BaseMessage]) -> str:
        """生成压缩摘要"""
        # 构建历史文本
        history_lines = []
        for msg in messages:
            role = self._get_role_name(msg)
            content = msg.content if isinstance(msg.content, str) else str(msg.content)
            # 截断过长的单条消息
            if len(content) > 500:
                content = content[:500] + "..."
            history_lines.append(f"[{role}] {content}")

        history_text = "\n".join(history_lines)

        # 构建压缩 prompt
        prompt = self.policy.compress_prompt_template.format(history=history_text)

        # 调用 LLM
        llm_messages = [
            SystemMessage(content="你是一个对话历史压缩专家，负责生成结构化的对话摘要。"),
            HumanMessage(content=prompt),
        ]

        response = await self.llm.ainvoke(llm_messages)
        summary = response.content.strip()

        return summary

    def _get_role_name(self, msg: BaseMessage) -> str:
        """获取消息角色名"""
        if isinstance(msg, HumanMessage):
            return "用户"
        elif isinstance(msg, AIMessage):
            name = getattr(msg, "name", None)
            return name if name else "AI"
        elif isinstance(msg, ToolMessage):
            return f"工具:{getattr(msg, 'name', 'unknown')}"
        elif isinstance(msg, SystemMessage):
            return "系统"
        return "未知"


# === 全局压缩器工厂 ===

_compactor_cache: Compactor | None = None


def get_compactor(policy: CompactPolicy | None = None) -> Compactor:
    """
    获取压缩器实例（带缓存）

    Args:
        policy: 压缩策略（可选）

    Returns:
        Compactor 实例
    """
    global _compactor_cache

    if _compactor_cache is None:
        from datapillar_oneagentic.providers.llm import call_llm

        llm = call_llm(temperature=0.0)
        _compactor_cache = Compactor(llm=llm, policy=policy or CompactPolicy())

    return _compactor_cache


def clear_compactor_cache() -> None:
    """清空压缩器缓存（仅测试用）"""
    global _compactor_cache
    _compactor_cache = None
