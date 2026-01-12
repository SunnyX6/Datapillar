"""
会话记忆 - 统一入口

整合 PinnedContext（不压缩）和 ConversationMemory（可压缩），
提供统一的上下文管理接口。

类似 Claude Code 的内存架构：
- 结构化信息（TODO、决策）→ 不压缩
- 对话历史 → 自动压缩

使用示例：
```python
memory = SessionMemory()

# 添加对话
memory.add_user_message("帮我创建用户表")
memory.add_agent_response("analyst", "好的，我来分析需求...")

# 固定关键信息（不会被压缩）
memory.pin_decision("使用 Iceberg 格式存储", "architect")
memory.pin_constraint("必须兼容现有 Hive 表")

# 检查是否需要压缩
if memory.needs_compact():
    result = await memory.compact()

# 生成上下文 prompt
prompt = memory.to_prompt()
```
"""

from __future__ import annotations

import logging
from typing import Callable

from pydantic import BaseModel, Field

from datapillar_oneagentic.providers.token_counter import get_token_counter
from datapillar_oneagentic.memory.compact_policy import CompactPolicy, CompactResult
from datapillar_oneagentic.memory.compactor import Compactor, get_compactor
from datapillar_oneagentic.memory.conversation import ConversationEntry, ConversationMemory
from datapillar_oneagentic.memory.pinned_context import ArtifactRef, Decision, PinnedContext
from datapillar_oneagentic.todo.todo_list import AgentTodoList

logger = logging.getLogger(__name__)


# 压缩前钩子类型
PreCompactHook = Callable[["SessionMemory"], None]


class SessionMemory(BaseModel):
    """
    会话记忆 - 统一入口

    整合固定上下文和对话历史，提供：
    - 分层存储：pinned（不压缩）+ conversation（可压缩）
    - 自动压缩：95% 阈值触发
    - 手动压缩：API 调用

    注意：session_id 和 user_id 由 Blackboard 管理，不在此存储。
    """

    # 固定上下文（不压缩）
    pinned: PinnedContext = Field(default_factory=PinnedContext)

    # 对话历史（可压缩）
    conversation: ConversationMemory = Field(default_factory=ConversationMemory)

    # 压缩策略
    policy: CompactPolicy = Field(default_factory=CompactPolicy)

    # 压缩摘要
    compressed_summary: str = Field(default="", description="压缩后的历史摘要")

    # 统计信息
    total_compactions: int = Field(default=0, description="压缩次数")
    total_tokens_saved: int = Field(default=0, description="累计节省的 token 数")

    # === 对话记录方法（代理到 ConversationMemory）===

    def add_user_message(self, content: str) -> ConversationEntry:
        """添加用户消息"""
        entry = self.conversation.append(
            speaker="user",
            listener="system",
            entry_type="user_message",
            content=content,
        )
        return entry

    def add_agent_response(self, agent_id: str, content: str) -> ConversationEntry:
        """添加 Agent 响应"""
        entry = self.conversation.append(
            speaker=agent_id,
            listener="user",
            entry_type="agent_response",
            content=content,
        )
        return entry

    def add_agent_handover(self, from_agent: str, to_agent: str, summary: str) -> ConversationEntry:
        """添加 Agent 交接"""
        entry = self.conversation.append(
            speaker=from_agent,
            listener=to_agent,
            entry_type="agent_handover",
            content=summary,
        )
        return entry

    def add_clarification(self, agent_id: str, question: str) -> ConversationEntry:
        """添加澄清问题"""
        entry = self.conversation.append(
            speaker=agent_id,
            listener="user",
            entry_type="clarification",
            content=question,
        )
        return entry

    def add_tool_result(self, agent_id: str, tool_name: str, result: str) -> ConversationEntry:
        """添加工具结果"""
        entry = self.conversation.append(
            speaker=f"{agent_id}:{tool_name}",
            listener=agent_id,
            entry_type="tool_result",
            content=result,
        )
        return entry

    # === 固定上下文方法（代理到 PinnedContext）===

    def pin_decision(self, content: str, agent_id: str) -> Decision:
        """固定关键决策"""
        return self.pinned.pin_decision(content, agent_id)

    def pin_constraint(self, constraint: str) -> None:
        """固定用户约束"""
        self.pinned.pin_constraint(constraint)

    def pin_artifact(self, ref_id: str, dtype: str, summary: str) -> ArtifactRef:
        """固定工件引用"""
        return self.pinned.pin_artifact(ref_id, dtype, summary)

    def set_todos(self, todos: AgentTodoList) -> None:
        """设置工作清单"""
        self.pinned.set_todos(todos)

    def get_todos(self) -> AgentTodoList | None:
        """获取工作清单"""
        return self.pinned.get_todos()

    # === 压缩相关方法 ===

    def estimate_tokens(self) -> int:
        """估算当前记忆的 token 数"""
        token_counter = get_token_counter()
        total = 0

        # 固定上下文
        pinned_prompt = self.pinned.to_prompt()
        if pinned_prompt:
            total += token_counter.count(pinned_prompt)

        # 对话历史
        if self.conversation:
            total += self.conversation.estimate_tokens()

        # 压缩摘要
        if self.compressed_summary:
            total += token_counter.count(self.compressed_summary)

        return total

    def needs_compact(self) -> bool:
        """判断是否需要压缩"""
        current_tokens = self.estimate_tokens()
        trigger_tokens = self.policy.get_trigger_tokens()
        return current_tokens > trigger_tokens

    async def compact(
        self,
        compactor: Compactor | None = None,
        pre_hooks: list[PreCompactHook] | None = None,
    ) -> CompactResult:
        """执行压缩"""
        # 运行 pre_compact 钩子
        if pre_hooks:
            for hook in pre_hooks:
                try:
                    hook(self)
                except Exception as e:
                    logger.warning(f"pre_compact 钩子执行失败: {e}")

        # 检查是否有可压缩的内容
        if not self.conversation or not self.conversation.entries:
            return CompactResult.no_action("没有对话记录")

        # 获取压缩器
        if compactor is None:
            compactor = get_compactor(self.policy)

        # 执行压缩
        result = await compactor.compress(
            entries=self.conversation.entries,
            existing_summary=self.compressed_summary,
        )

        if not result.success:
            return result

        # 更新状态
        if result.summary:
            self.compressed_summary = result.summary

            # 保留的条目
            _, compress_entries = compactor._classify_entries(self.conversation.entries)
            keep_entries = [e for e in self.conversation.entries if e not in compress_entries]
            self.conversation.entries = keep_entries

            # 更新统计
            self.total_compactions += 1
            self.total_tokens_saved += result.tokens_saved

            logger.info(
                f"📦 压缩完成: 移除 {result.removed_count} 条，"
                f"保留 {result.kept_count} 条，"
                f"节省 {result.tokens_saved} tokens"
            )

        return result

    # === Prompt 生成 ===

    def to_prompt(self, recent_limit: int = 20) -> str:
        """生成完整的上下文 prompt"""
        parts = []

        # 固定上下文
        pinned_prompt = self.pinned.to_prompt()
        if pinned_prompt:
            parts.append(pinned_prompt)

        # 历史摘要
        if self.compressed_summary:
            parts.append("## 历史摘要")
            parts.append(self.compressed_summary)

        # 最近对话
        if self.conversation:
            recent_entries = self.conversation.get_recent(recent_limit)
            if recent_entries:
                parts.append("## 最近对话")
                for entry in recent_entries:
                    parts.append(entry.to_display())

        return "\n\n".join(parts)

    def to_memory_prompt(self) -> str:
        """生成用于 AgentContext 的 memory_prompt"""
        return self.to_prompt()

    # === 序列化 ===

    def to_dict(self) -> dict:
        """转换为字典（用于存储）"""
        return self.model_dump(mode="json")

    @classmethod
    def from_dict(cls, data: dict) -> SessionMemory:
        """从字典恢复"""
        return cls.model_validate(data)

    # === 统计信息 ===

    def get_stats(self) -> dict:
        """获取统计信息"""
        return {
            "total_entries": len(self.conversation.entries) if self.conversation else 0,
            "total_decisions": len(self.pinned.decisions),
            "total_constraints": len(self.pinned.constraints),
            "total_artifacts": len(self.pinned.artifacts),
            "total_compactions": self.total_compactions,
            "total_tokens_saved": self.total_tokens_saved,
            "current_tokens": self.estimate_tokens(),
            "needs_compact": self.needs_compact(),
        }
