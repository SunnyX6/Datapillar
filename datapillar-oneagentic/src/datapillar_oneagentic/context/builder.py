"""
ContextBuilder - 统一的上下文构建器

协调 SessionMemory 和 Timeline 的操作，提供统一的 API。

设计原则：
- session_id, team_id, user_id 由 Blackboard 管理（单一来源）
- ContextBuilder 持有标识字段用于运行时操作
- 序列化时只保存 memory 和 timeline（分开存入 Blackboard）

使用示例：
```python
# 从 Blackboard 创建
builder = ContextBuilder.from_state(state)

# 操作
builder.add_user_message("帮我创建用户表")
builder.pin_decision("使用 Iceberg 格式", "architect")

# 写回 Blackboard
state["memory"] = builder.memory.to_dict()
state["timeline"] = builder.timeline.to_dict()
```
"""

from __future__ import annotations

import logging
import uuid
from typing import Any, TYPE_CHECKING

from pydantic import BaseModel, Field, ConfigDict

from datapillar_oneagentic.context.types import EventType, CheckpointType
from datapillar_oneagentic.context.memory import (
    SessionMemory,
    ConversationEntry,
    Decision,
    ArtifactRef,
)
from datapillar_oneagentic.context.timeline import (
    Timeline,
    TimelineEntry,
    TimeTravelRequest,
    TimeTravelResult,
)
from datapillar_oneagentic.context.checkpoint import CheckpointManager
from datapillar_oneagentic.todo.todo_list import AgentTodoList

if TYPE_CHECKING:
    from datapillar_oneagentic.state.blackboard import Blackboard

logger = logging.getLogger(__name__)


def _generate_id() -> str:
    return uuid.uuid4().hex[:12]


class ContextBuilder(BaseModel):
    """
    统一的上下文构建器

    协调 SessionMemory 和 Timeline，提供统一的操作 API。
    标识字段从 Blackboard 获取，不重复存储。
    """

    model_config = ConfigDict(arbitrary_types_allowed=True)

    # 运行时标识（从 Blackboard 获取，不序列化）
    session_id: str = Field(default="", description="会话 ID")
    team_id: str = Field(default="", description="团队 ID")
    user_id: str = Field(default="", description="用户 ID")

    # 子组件
    memory: SessionMemory = Field(default_factory=SessionMemory, description="会话记忆")
    timeline: Timeline = Field(default_factory=Timeline, description="执行时间线")

    # 内部（不序列化）
    _checkpoint_manager: CheckpointManager | None = None

    def set_checkpoint_manager(self, manager: CheckpointManager) -> None:
        """设置检查点管理器"""
        self._checkpoint_manager = manager

    # ========== 从 Blackboard 创建/恢复 ==========

    @classmethod
    def from_state(cls, state: "Blackboard") -> "ContextBuilder":
        """
        从 Blackboard 状态恢复 ContextBuilder

        参数：
        - state: Blackboard 图状态

        返回：
        - ContextBuilder 实例
        """
        session_id = state.get("session_id", "")
        team_id = state.get("team_id", "")
        user_id = state.get("user_id", "")

        # 恢复 memory
        memory_data = state.get("memory")
        if memory_data:
            memory = SessionMemory.model_validate(memory_data)
        else:
            memory = SessionMemory()

        # 恢复 timeline
        timeline_data = state.get("timeline")
        if timeline_data:
            timeline = Timeline.from_dict(timeline_data)
        else:
            timeline = Timeline()

        return cls(
            session_id=session_id,
            team_id=team_id,
            user_id=user_id,
            memory=memory,
            timeline=timeline,
        )

    def to_state_update(self) -> dict:
        """
        生成 Blackboard 状态更新

        返回：
        - 包含 memory 和 timeline 的字典，用于更新 Blackboard
        """
        return {
            "memory": self.memory.to_dict(),
            "timeline": self.timeline.to_dict(),
        }

    # ========== 事件记录 ==========

    def record_event(
        self,
        event_type: EventType,
        content: str,
        *,
        agent_id: str | None = None,
        duration_ms: int | None = None,
        metadata: dict | None = None,
    ) -> TimelineEntry:
        """记录事件到时间线"""
        return self.timeline.add_entry(
            event_type=event_type,
            content=content,
            agent_id=agent_id,
            duration_ms=duration_ms,
            metadata=metadata,
        )

    def create_checkpoint(
        self,
        content: str = "检查点",
        *,
        checkpoint_type: CheckpointType = CheckpointType.AUTO,
        agent_id: str | None = None,
    ) -> str:
        """
        创建检查点

        返回：
        - checkpoint_id
        """
        if self._checkpoint_manager:
            checkpoint_id = self._checkpoint_manager.generate_checkpoint_id(checkpoint_type)
        else:
            checkpoint_id = f"cp_{checkpoint_type.value[:3]}_{_generate_id()}"

        self.timeline.add_checkpoint(
            checkpoint_id=checkpoint_id,
            content=content,
            checkpoint_type=checkpoint_type,
            agent_id=agent_id,
        )

        logger.debug(f"创建检查点: {checkpoint_id}")
        return checkpoint_id

    # ========== 记忆操作（代理到 SessionMemory）==========

    def add_user_message(self, content: str) -> ConversationEntry:
        """添加用户消息"""
        entry = self.memory.add_user_message(content)
        self.record_event(EventType.USER_MESSAGE, content[:100])
        return entry

    def add_agent_response(self, agent_id: str, content: str) -> ConversationEntry:
        """添加 Agent 响应"""
        entry = self.memory.add_agent_response(agent_id, content)
        self.record_event(EventType.AGENT_END, content[:100], agent_id=agent_id)
        return entry

    def add_agent_handover(
        self,
        from_agent: str,
        to_agent: str,
        summary: str,
    ) -> ConversationEntry:
        """添加 Agent 交接"""
        entry = self.memory.add_agent_handover(from_agent, to_agent, summary)
        self.record_event(
            EventType.AGENT_HANDOVER,
            f"{from_agent} -> {to_agent}: {summary[:50]}",
            agent_id=from_agent,
        )
        return entry

    def add_clarification(self, agent_id: str, question: str) -> ConversationEntry:
        """添加澄清问题"""
        entry = self.memory.add_clarification(agent_id, question)
        self.record_event(EventType.CLARIFICATION, question[:100], agent_id=agent_id)
        return entry

    def add_tool_result(
        self,
        agent_id: str,
        tool_name: str,
        result: str,
    ) -> ConversationEntry:
        """添加工具结果"""
        entry = self.memory.add_tool_result(agent_id, tool_name, result)
        self.record_event(
            EventType.TOOL_RESULT,
            f"{tool_name}: {result[:50]}",
            agent_id=agent_id,
        )
        return entry

    def pin_decision(self, content: str, agent_id: str) -> Decision:
        """固定决策"""
        decision = self.memory.pin_decision(content, agent_id)
        self.record_event(EventType.DECISION, content[:100], agent_id=agent_id)
        return decision

    def pin_constraint(self, constraint: str) -> None:
        """固定约束"""
        self.memory.pin_constraint(constraint)
        self.record_event(EventType.CONSTRAINT, constraint[:100])

    def pin_artifact(self, ref_id: str, dtype: str, summary: str) -> ArtifactRef:
        """固定工件"""
        return self.memory.pin_artifact(ref_id, dtype, summary)

    def set_todos(self, todos: AgentTodoList) -> None:
        """设置 TODO"""
        self.memory.set_todos(todos)

    def get_todos(self) -> AgentTodoList | None:
        """获取 TODO"""
        return self.memory.get_todos()

    # ========== 时间旅行 ==========

    async def time_travel(
        self,
        request: TimeTravelRequest,
        compiled_graph=None,
    ) -> TimeTravelResult:
        """
        时间旅行到指定检查点

        参数：
        - request: 时间旅行请求
        - compiled_graph: 编译后的 LangGraph（用于状态恢复）
        """
        target_id = request.target_checkpoint_id

        # 检查检查点是否存在
        entry = self.timeline.get_entry_by_checkpoint(target_id)
        if not entry:
            return TimeTravelResult.failure_result(
                session_id=self.session_id,
                checkpoint_id=target_id,
                message=f"检查点不存在: {target_id}",
            )

        if request.create_branch:
            # 创建分支（新会话）
            new_session_id = f"{self.session_id}_branch_{_generate_id()}"
            return TimeTravelResult.success_result(
                session_id=new_session_id,
                checkpoint_id=target_id,
                message=f"创建分支: {request.branch_name or new_session_id}",
                is_branch=True,
                branch_name=request.branch_name or new_session_id,
            )
        else:
            # 直接回退
            removed_count = self.timeline.truncate_to_checkpoint(target_id)

            # 记录回退事件
            self.record_event(
                EventType.CHECKPOINT_RESTORE,
                f"回退到检查点: {target_id}",
            )

            return TimeTravelResult.success_result(
                session_id=self.session_id,
                checkpoint_id=target_id,
                removed_entries=removed_count,
                message=f"已回退到检查点，移除 {removed_count} 条事件",
            )

    def get_checkpoints(self) -> list[TimelineEntry]:
        """获取所有检查点"""
        return self.timeline.get_checkpoint_entries()

    def get_latest_checkpoint_id(self) -> str | None:
        """获取最新检查点 ID"""
        entry = self.timeline.get_latest_checkpoint()
        return entry.checkpoint_id if entry else None

    # ========== 统计和诊断 ==========

    def get_stats(self) -> dict:
        """获取统计信息"""
        memory_stats = self.memory.get_stats()
        timeline_stats = self.timeline.get_stats()

        return {
            "session_id": self.session_id,
            "team_id": self.team_id,
            "memory": memory_stats,
            "timeline": timeline_stats,
        }

    def to_prompt(self, include_timeline: bool = False) -> str:
        """
        生成给 Agent 的 prompt

        参数：
        - include_timeline: 是否包含时间线
        """
        parts = []

        # 记忆 prompt
        memory_prompt = self.memory.to_prompt()
        if memory_prompt:
            parts.append(memory_prompt)

        # 时间线 prompt（可选）
        if include_timeline:
            timeline_prompt = self.timeline.to_prompt(max_entries=10)
            if timeline_prompt:
                parts.append(timeline_prompt)

        return "\n\n".join(parts)

    # ========== 便捷方法 ==========

    def needs_compact(self) -> bool:
        """是否需要压缩"""
        return self.memory.needs_compact()

    async def compact(self) -> Any:
        """执行压缩"""
        result = await self.memory.compact()
        if result.success:
            self.record_event(
                EventType.MEMORY_COMPACT,
                f"压缩完成，节省 {result.tokens_saved} tokens",
            )
        return result

    def estimate_tokens(self) -> int:
        """估算 token 数"""
        return self.memory.estimate_tokens()
