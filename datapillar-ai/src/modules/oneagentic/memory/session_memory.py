"""
会话记忆 - 统一入口

整合 PinnedContext（不压缩）和 ConversationMemory（可压缩），
提供统一的上下文管理接口。

类似 Claude Code 的内存架构：
- 结构化信息（TODO、决策）→ 不压缩
- 对话历史 → 自动压缩

使用示例：
```python
memory = SessionMemory(session_id="xxx", user_id="yyy")

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
import time
from collections.abc import Callable
from typing import Any

from pydantic import BaseModel, Field

from src.infrastructure.llm.token_counter import estimate_text_tokens
from src.modules.oneagentic.memory.compact_policy import CompactPolicy, CompactResult
from src.modules.oneagentic.memory.compactor import Compactor, get_compactor
from src.modules.oneagentic.memory.conversation import ConversationEntry, ConversationMemory
from src.modules.oneagentic.memory.pinned_context import ArtifactRef, Decision, PinnedContext
from src.modules.oneagentic.todo.todo_list import AgentTodoList

logger = logging.getLogger(__name__)


def _now_ms() -> int:
    return int(time.time() * 1000)


# 压缩前钩子类型
PreCompactHook = Callable[["SessionMemory"], None]


class SessionMemory(BaseModel):
    """
    会话记忆 - 统一入口

    整合固定上下文和对话历史，提供：
    - 分层存储：pinned（不压缩）+ conversation（可压缩）
    - 自动压缩：95% 阈值触发
    - 手动压缩：API 调用
    """

    session_id: str = Field(..., description="会话 ID")
    user_id: str = Field(..., description="用户 ID")

    # 固定上下文（不压缩）
    pinned: PinnedContext = Field(default_factory=PinnedContext)

    # 对话历史（可压缩）
    conversation: ConversationMemory | None = Field(default=None)

    # 压缩策略
    policy: CompactPolicy = Field(default_factory=CompactPolicy)

    # 压缩摘要
    compressed_summary: str = Field(default="", description="压缩后的历史摘要")

    # 统计信息
    total_compactions: int = Field(default=0, description="压缩次数")
    total_tokens_saved: int = Field(default=0, description="累计节省的 token 数")

    # 时间戳
    created_at_ms: int = Field(default_factory=_now_ms)
    updated_at_ms: int = Field(default_factory=_now_ms)

    def model_post_init(self, __context: Any) -> None:
        """初始化后处理"""
        if self.conversation is None:
            self.conversation = ConversationMemory(
                session_id=self.session_id,
                user_id=self.user_id,
            )

    # === 对话记录方法（代理到 ConversationMemory）===

    def add_user_message(self, content: str) -> ConversationEntry:
        """添加用户消息"""
        entry = self.conversation.append(
            speaker="user",
            listener="system",
            entry_type="user_message",
            content=content,
        )
        self.updated_at_ms = _now_ms()
        return entry

    def add_agent_response(self, agent_id: str, content: str) -> ConversationEntry:
        """添加 Agent 响应"""
        entry = self.conversation.append(
            speaker=agent_id,
            listener="user",
            entry_type="agent_response",
            content=content,
        )
        self.updated_at_ms = _now_ms()
        return entry

    def add_agent_handover(self, from_agent: str, to_agent: str, summary: str) -> ConversationEntry:
        """添加 Agent 交接"""
        entry = self.conversation.append(
            speaker=from_agent,
            listener=to_agent,
            entry_type="agent_handover",
            content=summary,
        )
        self.updated_at_ms = _now_ms()
        return entry

    def add_clarification(self, agent_id: str, question: str) -> ConversationEntry:
        """添加澄清问题"""
        entry = self.conversation.append(
            speaker=agent_id,
            listener="user",
            entry_type="clarification",
            content=question,
        )
        self.updated_at_ms = _now_ms()
        return entry

    def add_tool_result(self, agent_id: str, tool_name: str, result: str) -> ConversationEntry:
        """添加工具结果"""
        entry = self.conversation.append(
            speaker=f"{agent_id}:{tool_name}",
            listener=agent_id,
            entry_type="tool_result",
            content=result,
        )
        self.updated_at_ms = _now_ms()
        return entry

    # === 固定上下文方法（代理到 PinnedContext）===

    def pin_decision(self, content: str, agent_id: str) -> Decision:
        """固定关键决策"""
        decision = self.pinned.pin_decision(content, agent_id)
        self.updated_at_ms = _now_ms()
        return decision

    def pin_constraint(self, constraint: str) -> None:
        """固定用户约束"""
        self.pinned.pin_constraint(constraint)
        self.updated_at_ms = _now_ms()

    def pin_artifact(self, ref_id: str, dtype: str, summary: str) -> ArtifactRef:
        """固定工件引用"""
        artifact = self.pinned.pin_artifact(ref_id, dtype, summary)
        self.updated_at_ms = _now_ms()
        return artifact

    def set_todos(self, todos: AgentTodoList) -> None:
        """设置工作清单"""
        self.pinned.set_todos(todos)
        self.updated_at_ms = _now_ms()

    def get_todos(self) -> AgentTodoList | None:
        """获取工作清单"""
        return self.pinned.get_todos()

    # === 压缩相关方法 ===

    def estimate_tokens(self) -> int:
        """
        估算当前记忆的 token 数

        包括：固定上下文 + 对话历史 + 压缩摘要
        """
        total = 0

        # 固定上下文
        pinned_prompt = self.pinned.to_prompt()
        if pinned_prompt:
            total += estimate_text_tokens(text=pinned_prompt)

        # 对话历史
        if self.conversation:
            total += self.conversation.estimate_tokens()

        # 压缩摘要
        if self.compressed_summary:
            total += estimate_text_tokens(text=self.compressed_summary)

        return total

    def needs_compact(self) -> bool:
        """
        判断是否需要压缩

        当总 token 数超过 policy.trigger_threshold 时返回 True。
        """
        current_tokens = self.estimate_tokens()
        trigger_tokens = self.policy.get_trigger_tokens()
        return current_tokens > trigger_tokens

    async def compact(
        self,
        compactor: Compactor | None = None,
        pre_hooks: list[PreCompactHook] | None = None,
    ) -> CompactResult:
        """
        执行压缩

        1. 运行 pre_compact 钩子
        2. 分离固定上下文和可压缩内容
        3. 调用压缩器生成摘要
        4. 更新状态

        Args:
            compactor: 压缩器（可选，默认使用全局压缩器）
            pre_hooks: 压缩前钩子列表

        Returns:
            CompactResult: 压缩结果
        """
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
            self.updated_at_ms = _now_ms()

            logger.info(
                f"📦 压缩完成: 移除 {result.removed_count} 条，"
                f"保留 {result.kept_count} 条，"
                f"节省 {result.tokens_saved} tokens"
            )

        return result

    # === Prompt 生成 ===

    def to_prompt(self, recent_limit: int = 20) -> str:
        """
        生成完整的上下文 prompt

        格式：
        ## 固定上下文
        [决策、约束、TODO、工件]

        ## 历史摘要
        [压缩后的历史摘要]

        ## 最近对话
        [最近的对话记录]

        Args:
            recent_limit: 最近对话的条数限制

        Returns:
            完整的上下文 prompt
        """
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
        """
        生成用于 AgentContext 的 memory_prompt

        这是 to_prompt() 的别名，保持向后兼容。
        """
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
            "session_id": self.session_id,
            "total_entries": len(self.conversation.entries) if self.conversation else 0,
            "total_decisions": len(self.pinned.decisions),
            "total_constraints": len(self.pinned.constraints),
            "total_artifacts": len(self.pinned.artifacts),
            "total_compactions": self.total_compactions,
            "total_tokens_saved": self.total_tokens_saved,
            "current_tokens": self.estimate_tokens(),
            "needs_compact": self.needs_compact(),
        }
