"""
SessionMemory - 短期记忆（通过 Checkpointer 持久化）

存储内容：
- 需求 TODO 清单（简化版）
- 产物状态（哪个 Agent 完成了什么）
- 按 Agent 隔离的对话记忆（最近几轮 + 压缩摘要）

不存储：
- SQL 原文（太大，在 Handover 中运行时传递）
- Workflow JSON（太大，在 Handover 中运行时传递）

设计原则：
- 按 Agent 隔离：每个 Agent 有独立的对话历史和压缩摘要
- 压缩时机：达到该 Agent 上下文阈值时触发 LLM 压缩
- 总大小控制在 ~5-8KB
"""

from __future__ import annotations

import time
from typing import Any, Literal

from pydantic import BaseModel, Field


class AgentStatus(BaseModel):
    """Agent 产物状态（不存原文，只存状态）"""

    # 注意：这里存的是“编排视角”状态，用于回放/观测/续跑。
    # 编排器会写入 AgentResult.status（含 needs_*），因此必须与之兼容。
    status: Literal[
        "pending",
        "in_progress",
        "completed",
        "failed",
        "needs_clarification",
        "needs_delegation",
        "waiting",
        "blocked",
    ] = "pending"
    deliverable_type: str | None = None  # "analysis" / "workflow" / "sql" / "test"
    summary: str = ""  # 简短摘要
    updated_at_ms: int = Field(default_factory=lambda: int(time.time() * 1000))


class AgentConversationMemory(BaseModel):
    """
    Agent 级别的对话记忆（按 Agent 隔离）

    设计原则：
    - recent_turns：最近几轮对话（未压缩，用于保持连贯性）
    - compressed_summary：历史对话的压缩摘要（LLM 生成）
    - 当 recent_turns 达到阈值时，触发压缩，合并到 compressed_summary
    """

    # 最近几轮对话（未压缩）
    recent_turns: list[dict[str, str]] = Field(
        default_factory=list,
        description="最近几轮对话，格式：[{'role': 'user'/'assistant', 'content': '...'}]",
    )

    # 历史对话的压缩摘要（LLM 生成）
    compressed_summary: str = Field(
        default="",
        description="历史对话的压缩摘要",
    )

    # 压缩次数（用于追踪）
    compression_count: int = Field(default=0)

    # 更新时间
    updated_at_ms: int = Field(default_factory=lambda: int(time.time() * 1000))

    def add_turn(self, role: str, content: str, max_recent: int = 10) -> None:
        """添加一轮对话"""
        self.recent_turns.append(
            {
                "role": role,
                "content": content[:2000],  # 限制单条长度
            }
        )
        # 只保留最近 max_recent 轮
        if len(self.recent_turns) > max_recent:
            self.recent_turns = self.recent_turns[-max_recent:]
        self.updated_at_ms = int(time.time() * 1000)

    def get_context(self) -> dict[str, Any]:
        """获取用于注入 prompt 的上下文"""
        return {
            "compressed_summary": self.compressed_summary,
            "recent_turns": self.recent_turns,
        }

    def apply_compression(self, new_summary: str) -> None:
        """应用压缩结果：清空 recent_turns，更新 compressed_summary"""
        self.compressed_summary = new_summary[:1000]  # 限制摘要长度
        self.recent_turns = []  # 清空已压缩的对话
        self.compression_count += 1
        self.updated_at_ms = int(time.time() * 1000)


class SessionMemory(BaseModel):
    """
    短期记忆（Session 级别，通过 Checkpointer 持久化）

    设计原则：
    - 按 Agent 隔离：每个 Agent 有独立的对话记忆
    - 只存"小而精"的状态/摘要
    - 不存原文（SQL、Workflow JSON 等）
    - 总大小控制在 ~5-8KB
    """

    session_id: str = Field(..., description="会话ID")

    # ==================== 需求 TODO 清单 ====================
    requirement_todos: list[dict[str, Any]] = Field(
        default_factory=list,
        description="需求 TODO 清单（简化版）",
    )
    requirement_revision: int = Field(
        default=0,
        description="需求版本号",
    )

    # ==================== 产物状态 ====================
    agent_statuses: dict[str, AgentStatus] = Field(
        default_factory=dict,
        description="各 Agent 的产物状态",
    )

    # ==================== 按 Agent 隔离的对话记忆 ====================
    agent_conversations: dict[str, AgentConversationMemory] = Field(
        default_factory=dict,
        description="各 Agent 的对话记忆（按 agent_id 隔离）",
    )

    # ==================== 元信息 ====================
    created_at_ms: int = Field(default_factory=lambda: int(time.time() * 1000))
    updated_at_ms: int = Field(default_factory=lambda: int(time.time() * 1000))

    model_config = {"arbitrary_types_allowed": True}

    # ==================== 需求 TODO 操作 ====================

    def update_requirement_todos(
        self,
        todos: list[dict[str, Any]],
        revision: int,
    ) -> None:
        """更新需求 TODO 清单"""
        simplified = []
        for todo in todos:
            simplified.append(
                {
                    "id": todo.get("id", ""),
                    "title": todo.get("title", "")[:100],
                    "status": todo.get("status", "open"),
                    "type": todo.get("type", "task"),
                }
            )
        self.requirement_todos = simplified
        self.requirement_revision = revision
        self.updated_at_ms = int(time.time() * 1000)

    # ==================== 产物状态操作 ====================

    def update_agent_status(
        self,
        agent_id: str,
        status: str,
        deliverable_type: str | None = None,
        summary: str = "",
    ) -> None:
        """更新 Agent 产物状态"""
        self.agent_statuses[agent_id] = AgentStatus(
            status=status,  # type: ignore
            deliverable_type=deliverable_type,
            summary=summary[:200],
        )
        self.updated_at_ms = int(time.time() * 1000)

    def get_agent_status(self, agent_id: str) -> AgentStatus | None:
        """获取 Agent 产物状态"""
        return self.agent_statuses.get(agent_id)

    def is_agent_completed(self, agent_id: str) -> bool:
        """检查 Agent 是否完成"""
        status = self.agent_statuses.get(agent_id)
        return status is not None and status.status == "completed"

    # ==================== Agent 对话记忆操作 ====================

    def get_agent_conversation(self, agent_id: str) -> AgentConversationMemory:
        """获取指定 Agent 的对话记忆（不存在则创建）"""
        if agent_id not in self.agent_conversations:
            self.agent_conversations[agent_id] = AgentConversationMemory()
        return self.agent_conversations[agent_id]

    def add_agent_turn(self, agent_id: str, role: str, content: str) -> None:
        """添加一轮对话到指定 Agent"""
        conv = self.get_agent_conversation(agent_id)
        conv.add_turn(role, content)
        self.updated_at_ms = int(time.time() * 1000)

    def apply_agent_compression(self, agent_id: str, new_summary: str) -> None:
        """应用压缩结果到指定 Agent"""
        conv = self.get_agent_conversation(agent_id)
        conv.apply_compression(new_summary)
        self.updated_at_ms = int(time.time() * 1000)

    def get_agent_context(self, agent_id: str) -> dict[str, Any]:
        """获取指定 Agent 的上下文（用于注入 prompt）"""
        conv = self.get_agent_conversation(agent_id)
        return {
            "conversation": conv.get_context(),
            "requirement_todos": self.requirement_todos,
            "requirement_revision": self.requirement_revision,
        }

    # ==================== 序列化 ====================

    def to_context_string(self, agent_id: str | None = None) -> str:
        """转换为上下文字符串（供 LLM 使用）"""
        lines = []

        # 需求 TODO
        if self.requirement_todos:
            lines.append("## 需求 TODO")
            for todo in self.requirement_todos:
                status_icon = "✅" if todo.get("status") == "done" else "⏳"
                lines.append(f"- {status_icon} {todo.get('title', '')}")

        # Agent 状态
        if self.agent_statuses:
            lines.append("\n## 进度")
            for aid, status in self.agent_statuses.items():
                lines.append(f"- {aid}: {status.status} - {status.summary}")

        # 指定 Agent 的对话摘要
        if agent_id:
            conv = self.agent_conversations.get(agent_id)
            if conv and conv.compressed_summary:
                lines.append(f"\n## 历史对话摘要\n{conv.compressed_summary}")

        return "\n".join(lines)
