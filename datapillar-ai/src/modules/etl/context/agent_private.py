"""
AgentPrivate - 员工私有存储

存储员工的私有信息，老板（Boss）不知道这些内容。

包含：
- 对话历史：用户和这个 Agent 的聊天记录
- 工具缓存：避免重复调用相同工具
- 中间状态：Agent 内部的临时状态

存储方式：
- 按 Agent 隔离
- Redis key 格式：etl:{session_id}:agent:{agent_id}:private
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

ConversationRole = Literal["user", "assistant"]


class ConversationTurn(BaseModel):
    """对话轮次"""

    role: ConversationRole
    content: str
    created_at_ms: int
    message_id: str | None = None
    chat_session_id: str | None = None


class AgentPrivate(BaseModel):
    """
    员工私有存储

    这些信息只有 Agent 自己知道，老板不知道。
    """

    agent_id: str = Field(..., description="员工ID")
    session_id: str = Field(default="", description="会话ID")

    # ==================== 对话历史 ====================
    conversation: list[ConversationTurn] = Field(
        default_factory=list,
        description="用户和这个 Agent 的对话历史",
    )
    chat_session_id: str | None = Field(
        None,
        description="当前聊天段ID（用于结束聊天段而不清历史）",
    )

    # ==================== 工具缓存 ====================
    tool_cache: dict[str, Any] = Field(
        default_factory=dict,
        description="工具调用结果缓存（避免重复调用）",
    )

    # ==================== 中间状态 ====================
    intermediate_state: dict[str, Any] = Field(
        default_factory=dict,
        description="Agent 内部的临时状态",
    )

    model_config = {"arbitrary_types_allowed": True}

    # ==================== 对话操作 ====================

    def add_turn(
        self,
        role: ConversationRole,
        content: str,
        created_at_ms: int,
        message_id: str | None = None,
    ) -> None:
        """添加对话轮次"""
        turn = ConversationTurn(
            role=role,
            content=content,
            created_at_ms=created_at_ms,
            message_id=message_id,
            chat_session_id=self.chat_session_id,
        )
        self.conversation.append(turn)

    def get_recent_turns(self, max_turns: int = 10) -> list[ConversationTurn]:
        """获取最近的对话轮次"""
        return self.conversation[-max_turns:]

    def get_session_turns(self) -> list[ConversationTurn]:
        """获取当前聊天段的对话"""
        if not self.chat_session_id:
            return self.conversation
        return [t for t in self.conversation if t.chat_session_id == self.chat_session_id]

    def rotate_chat_session(self, new_session_id: str) -> None:
        """
        旋转聊天段

        结束当前聊天段，开启新的聊天段。
        历史保留但不再注入到 prompt。
        """
        self.chat_session_id = new_session_id

    def clear_conversation(self) -> None:
        """清空对话历史"""
        self.conversation.clear()

    # ==================== 工具缓存操作 ====================

    def cache_tool_result(self, cache_key: str, result: Any) -> None:
        """缓存工具调用结果"""
        self.tool_cache[cache_key] = result

    def get_tool_cache(self, cache_key: str) -> Any | None:
        """获取缓存的工具结果"""
        return self.tool_cache.get(cache_key)

    def clear_tool_cache(self) -> None:
        """清空工具缓存"""
        self.tool_cache.clear()

    # ==================== 中间状态操作 ====================

    def set_state(self, key: str, value: Any) -> None:
        """设置中间状态"""
        self.intermediate_state[key] = value

    def get_state(self, key: str, default: Any = None) -> Any:
        """获取中间状态"""
        return self.intermediate_state.get(key, default)

    def clear_state(self) -> None:
        """清空中间状态"""
        self.intermediate_state.clear()
