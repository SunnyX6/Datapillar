"""
压缩器 - 负责执行对话历史压缩

所有 Agent 共用同一个压缩器，确保压缩行为一致。

压缩流程：
1. 分类条目：根据 policy 分为保留和压缩两类
2. 生成摘要：使用 LLM 压缩需要压缩的部分
3. 重组历史：摘要 + 保留的条目
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Any

from langchain_core.messages import HumanMessage as LCHumanMessage
from langchain_core.messages import SystemMessage as LCSystemMessage

from src.infrastructure.llm.token_counter import estimate_text_tokens
from src.modules.oneagentic.memory.compact_policy import CompactPolicy, CompactResult

if TYPE_CHECKING:
    from src.modules.oneagentic.memory.conversation import ConversationEntry

logger = logging.getLogger(__name__)


class Compactor:
    """
    压缩器

    负责执行对话历史的压缩，包括：
    - 分类条目（保留 vs 压缩）
    - 调用 LLM 生成摘要
    - 计算 token 使用量
    """

    def __init__(self, llm: Any, policy: CompactPolicy | None = None):
        """
        初始化压缩器

        Args:
            llm: LLM 实例
            policy: 压缩策略（默认使用 CompactPolicy()）
        """
        self.llm = llm
        self.policy = policy or CompactPolicy()

    async def compress(
        self,
        entries: list[ConversationEntry],
        existing_summary: str = "",
    ) -> CompactResult:
        """
        执行压缩

        Args:
            entries: 对话条目列表
            existing_summary: 已有的压缩摘要（会合并）

        Returns:
            CompactResult: 压缩结果
        """
        if not entries:
            return CompactResult.no_action("没有对话记录")

        # 计算压缩前 token 数
        tokens_before = self._estimate_entries_tokens(entries)
        if existing_summary:
            tokens_before += estimate_text_tokens(text=existing_summary)

        # 检查是否需要压缩
        if tokens_before <= self.policy.get_target_tokens():
            return CompactResult.no_action(f"当前 {tokens_before} tokens，无需压缩")

        # 分类条目
        keep_entries, compress_entries = self._classify_entries(entries)

        if not compress_entries:
            return CompactResult.no_action("没有可压缩的条目")

        # 生成压缩摘要
        try:
            summary = await self._generate_summary(compress_entries, existing_summary)
        except Exception as e:
            logger.error(f"压缩失败: {e}", exc_info=True)
            return CompactResult.failed(str(e))

        # 计算压缩后 token 数
        tokens_after = estimate_text_tokens(text=summary)
        tokens_after += self._estimate_entries_tokens(keep_entries)

        return CompactResult(
            success=True,
            summary=summary,
            kept_count=len(keep_entries),
            removed_count=len(compress_entries),
            tokens_before=tokens_before,
            tokens_after=tokens_after,
            tokens_saved=tokens_before - tokens_after,
        )

    def _classify_entries(
        self,
        entries: list[ConversationEntry],
    ) -> tuple[list[ConversationEntry], list[ConversationEntry]]:
        """
        分类条目

        根据 policy.keep_categories 分类：
        - keep_entries: 保留原文的条目
        - compress_entries: 需要压缩的条目

        最近的 min_keep_entries 条始终保留。

        Args:
            entries: 所有条目

        Returns:
            (保留的条目, 需要压缩的条目)
        """
        min_keep = self.policy.min_keep_entries

        # 最近的条目始终保留
        if len(entries) <= min_keep:
            return entries.copy(), []

        recent_entries = entries[-min_keep:]
        older_entries = entries[:-min_keep]

        keep_entries = []
        compress_entries = []

        for entry in older_entries:
            if self.policy.should_keep_entry(entry.entry_type):
                keep_entries.append(entry)
            else:
                compress_entries.append(entry)

        # 合并：保留的 + 最近的
        keep_entries.extend(recent_entries)

        return keep_entries, compress_entries

    async def _generate_summary(
        self,
        entries: list[ConversationEntry],
        existing_summary: str = "",
    ) -> str:
        """
        生成压缩摘要

        Args:
            entries: 需要压缩的条目
            existing_summary: 已有的摘要（会合并）

        Returns:
            压缩后的摘要
        """
        # 构建历史文本
        history_lines = []
        if existing_summary:
            history_lines.append("[之前的历史摘要]")
            history_lines.append(existing_summary)
            history_lines.append("")
            history_lines.append("[新增的对话]")

        for entry in entries:
            history_lines.append(entry.to_display())

        history_text = "\n".join(history_lines)

        # 构建压缩 prompt
        prompt = self.policy.compress_prompt_template.format(history=history_text)

        # 调用 LLM
        messages = [
            LCSystemMessage(content="你是一个对话历史压缩专家，负责生成结构化的对话摘要。"),
            LCHumanMessage(content=prompt),
        ]

        response = await self.llm.ainvoke(messages)
        summary = response.content.strip()

        # 限制摘要长度
        max_tokens = self.policy.max_summary_tokens
        while estimate_text_tokens(text=summary) > max_tokens:
            # 按段落截断
            paragraphs = summary.split("\n\n")
            if len(paragraphs) <= 1:
                # 硬截断
                summary = summary[: max_tokens * 2]  # 粗略估计 1 token ≈ 2 字符
                break
            summary = "\n\n".join(paragraphs[:-1])

        return summary

    def _estimate_entries_tokens(self, entries: list[ConversationEntry]) -> int:
        """估算条目列表的 token 数"""
        if not entries:
            return 0

        total = 0
        for entry in entries:
            # 内容 token + 格式开销（约 30 tokens）
            total += estimate_text_tokens(text=entry.content) + 30
        return total

    def estimate_total_tokens(
        self,
        entries: list[ConversationEntry],
        summary: str = "",
    ) -> int:
        """
        估算总 token 数

        Args:
            entries: 对话条目
            summary: 压缩摘要

        Returns:
            总 token 数
        """
        total = self._estimate_entries_tokens(entries)
        if summary:
            total += estimate_text_tokens(text=summary)
        return total


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
        from src.infrastructure.llm.client import call_llm

        llm = call_llm(temperature=0.0)
        _compactor_cache = Compactor(llm=llm, policy=policy or CompactPolicy())

    return _compactor_cache


def clear_compactor_cache() -> None:
    """清空压缩器缓存（仅测试用）"""
    global _compactor_cache
    _compactor_cache = None
